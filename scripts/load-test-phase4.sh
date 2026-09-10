#!/usr/bin/env bash
#
# Phase 4 동시 요청 부하 비교 스크립트.
#
# 세 갈래를 각각 동시 N건 호출해서 비교한다:
#   1. /parallel               — 기존 WebClient (suspend, 논블로킹)
#   2. /parallel-blocking      — RestTemplate + withContext(Dispatchers.IO) (suspend)
#   3. /parallel-blocking-naive — RestTemplate + runBlocking (plain fun, 안티패턴)
#
# 기대 결과: 1/2번은 server.tomcat.threads.max(=10)를 낮춰도 busy 스레드가 낮게 유지되고
# 응답시간이 개별 요청 수준으로 고르지만, 3번(naive)은 busy 스레드가 max 근처까지 치솟고
# 동시 요청 수가 max를 넘는 만큼 요청이 큐잉되어 응답시간이 계단식으로 늘어난다.
#
# 주의: 매 요청이 실제 외부 API(country 등)를 그대로 호출한다(캐싱은 Phase 5 이후 범위).
# 세 배치를 쉬지 않고 연달아 쏘면 country API(REST Countries, Cloudflare)가 짧은 시간에
# 몰린 요청을 429로 차단하고, 그게 애플리케이션에서는 500으로 전파되어 "톰캣 스레드 풀
# 소진"과 겉보기엔 비슷한 실패로 섞여 보일 수 있다. 이를 피하려고 배치 사이에
# COOLDOWN_SECONDS만큼 쉰다 — 만약 그래도 실패가 보이면 우리 쪽 문제가 아니라 외부 API
# 레이트리밋일 가능성이 높으니 서버 로그에서 429/TooManyRequests를 먼저 확인할 것.
#
# 사용법: ./scripts/load-test-phase4.sh [동시 요청 수(기본 15)]
# 전제: ./gradlew bootRun으로 서버가 이미 8080 포트에 떠 있어야 한다.

set -euo pipefail

BASE_URL="http://localhost:8080/api/dashboard"
BUSY_URL="http://localhost:8080/actuator/metrics/tomcat.threads.busy"
# tomcat.threads.busy는 톰캣 커넥터 워커 풀(http-nio-8080-exec-*)만 본다. RestTemplate을
# withContext(Dispatchers.IO)로 감싸면 블로킹 자체가 사라지는 게 아니라 그 블로킹이 일어나는
# 위치가 톰캣 풀에서 Dispatchers.IO 풀로 옮겨갈 뿐이다 — 이걸 확인하려고 JVM 전체 살아있는
# 스레드 수(jvm.threads.live)도 같이 본다. Dispatchers.IO는 별도 스레드를 새로 만들어 쓰므로,
# 거기서 블로킹이 늘어나면 톰캣과 무관하게 이 값이 올라가는 게 보여야 한다.
JVM_THREADS_URL="http://localhost:8080/actuator/metrics/jvm.threads.live"
CONCURRENCY="${1:-15}"
COOLDOWN_SECONDS="${2:-10}"
QS="city=Seoul&countryName=South%20Korea&countryCode=KR&baseCurrency=KRW"
RESULT_DIR="$(mktemp -d)"

trap 'rm -rf "$RESULT_DIR"' EXIT

metric_value() {
	local url="$1"
	local body
	body=$(curl -s -m 2 "$url" 2>/dev/null) || return 0
	echo "$body" | sed -n 's/.*"value":\([0-9.]*\).*/\1/p'
}

run_batch() {
	local label="$1" path="$2"
	local req_dir="${RESULT_DIR}/${label}-req"
	local busy_file="${RESULT_DIR}/${label}-busy.log"
	local jvm_file="${RESULT_DIR}/${label}-jvm.log"

	echo "=== ${label} (동시 ${CONCURRENCY}건 → ${path}) ==="

	mkdir -p "$req_dir"
	: >"$busy_file"
	: >"$jvm_file"

	# 부하 도중 0.1초 간격으로 (1) 톰캣 busy 스레드 수 (2) JVM 전체 살아있는 스레드 수를
	# 같은 주기로 함께 기록한다. 찰나의 스파이크와 몇 초간 지속되는 점유를 구분하려면
	# 최댓값 하나보다 전체 흐름을 봐야 한다.
	(
		while true; do
			metric_value "$BUSY_URL" >>"$busy_file"
			metric_value "$JVM_THREADS_URL" >>"$jvm_file"
			sleep 0.1
		done
	) &
	local poller_pid=$!

	local start_ts
	start_ts=$(date +%s%N)

	# 각 요청은 자기 전용 파일에만 써서(공유 파일 append 시 발생할 수 있는 동시 쓰기
	# 인터리빙을 원천 차단) 결과를 모은 뒤 마지막에 합친다.
	local pids=()
	local i
	for i in $(seq 1 "$CONCURRENCY"); do
		curl -s -m 60 -o /dev/null -w "%{http_code} %{time_total}\n" \
			"${BASE_URL}/${path}?${QS}" >"${req_dir}/${i}.txt" &
		pids+=("$!")
	done

	for pid in "${pids[@]}"; do
		wait "$pid" || true
	done

	local end_ts
	end_ts=$(date +%s%N)

	kill "$poller_pid" 2>/dev/null || true
	wait "$poller_pid" 2>/dev/null || true

	local out_file="${RESULT_DIR}/${label}.txt"
	cat "${req_dir}"/*.txt >"$out_file"

	echo "전체 벽시계 소요: $(((end_ts - start_ts) / 1000000))ms"

	local fail_count
	fail_count=$(awk '$1 != 200 { c++ } END { print c + 0 }' "$out_file")
	if [ "$fail_count" -gt 0 ]; then
		echo "경고: HTTP 200이 아닌 응답 ${fail_count}건 발생 — 톰캣 스레드 고갈이 아니라"
		echo "외부 API 레이트리밋(429)일 수 있다. 서버 로그에서 TooManyRequests를 확인할 것."
	fi

	echo "개별 응답시간(초, 오름차순):"
	awk '{print $2}' "$out_file" | sort -n | tr '\n' ' '
	echo

	local max_busy max_jvm
	max_busy=$(sort -n "$busy_file" | tail -1)
	max_jvm=$(sort -n "$jvm_file" | tail -1)
	echo "부하 도중 관측된 최대 톰캣 busy 스레드 수: ${max_busy:-N/A}"
	echo "부하 도중 관측된 최대 JVM 전체 스레드 수: ${max_jvm:-N/A}"

	echo "톰캣 busy 스레드 시계열(0.1초 간격, 시간순):"
	tr '\n' ' ' <"$busy_file"
	echo
	echo "JVM 전체 스레드 수 시계열(0.1초 간격, 시간순):"
	tr '\n' ' ' <"$jvm_file"
	echo
	echo "  → 톰캣 busy는 낮은데 JVM 전체 스레드 수가 같이 튀면, 블로킹이 사라진 게 아니라"
	echo "    Dispatchers.IO 같은 다른 풀로 옮겨갔을 뿐이라는 뜻이다. 값이 잠깐 튀었다가"
	echo "    바로 내려가면 순간적인 스파이크, 여러 샘플 연속으로 높게 유지되면 실제로"
	echo "    오래 점유된 것이다."
	echo
}

echo "부하 테스트 대상: ${BASE_URL}"
echo "동시 요청 수: ${CONCURRENCY} (배치 사이 쿨다운 ${COOLDOWN_SECONDS}초)"
echo

run_batch "1-webclient" "parallel"
sleep "$COOLDOWN_SECONDS"
run_batch "2-resttemplate-io" "parallel-blocking"
sleep "$COOLDOWN_SECONDS"
run_batch "3-resttemplate-naive" "parallel-blocking-naive"

echo "완료. 1/2번은 busy 스레드가 낮게 유지되고 응답시간이 고르면 정상,"
echo "3번(naive)만 busy 스레드가 설정된 max 근처까지 치솟고 응답시간이 계단식으로 늘어나면"
echo "Phase 4가 보여주려는 톰캣 스레드 풀 소진 차이가 확인된 것이다."
