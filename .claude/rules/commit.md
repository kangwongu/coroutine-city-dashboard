# 커밋 메시지 컨벤션

## 형식

```
type: 제목 (한글, 50자 이내)

본문 (선택, 줄당 72자 이내)
```

## Type 목록

| type | 용도 |
|------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `chore` | 설정, 의존성, 빌드, 환경 변경 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서, 주석 추가/수정 |
| `remove` | 코드/파일 삭제 |
| `style` | 포맷, 네이밍 등 코드 스타일 변경 (로직 변경 없음) |

## 규칙

- 제목은 **한글**로 작성한다.
- 제목은 **50자 이내**, 마침표 없이 작성한다.
- 제목은 **명령형**으로 작성한다. (예: "추가하다", "삭제하다", "수정하다")
- 본문이 필요한 경우 제목과 빈 줄로 구분한다.
- 본문에는 **무엇을, 왜** 변경했는지 설명한다. (어떻게는 코드로 표현)
- 한 커밋에는 **하나의 논리적 변경**만 포함한다.

## 예시

```
refactor: CryptoAsset에서 sectorList/ecosystemList 필드 제거
```

```
remove: CryptoAssetResCommand 내부 SectorResCommand/EcosystemResCommand 레코드 삭제
```

```
fix: AssetResCommand compact constructor 잔재 코드 제거
```

```
test: CryptoCaveMapper 삭제된 오버로드 부재 검증 테스트 추가
```

```
style: CryptoAsset4CommunityRes @author 태그 제거 및 @Builder 어노테이션 삭제
```

## 금지 사항

- `WIP`, `임시`, `수정`, `작업중` 등 의미 없는 제목 금지
- `fix: 버그 수정`, `refactor: 리팩토링` 등 type과 제목이 중복되는 표현 금지
- 영어와 한글 혼용 금지 (코드 용어·라이브러리명 등 고유명사 제외)
- 제목과 본문은 **한국어로만** 작성한다 (영어 단독 사용 금지)
- `Co-Authored-By:` 태그 추가 금지
- 제목 **50자 초과** 금지
- **이모지** 사용 금지
