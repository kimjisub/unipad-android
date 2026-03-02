# UniPad 테마 제작 가이드

UniPad 앱의 외관을 커스터마이징하는 ZIP 테마를 제작하는 방법을 설명합니다.

## 파일 구조

```
my-theme.zip
├── theme.json          (필수) 테마 메타데이터
├── theme_ic.png        (필수) 테마 아이콘/썸네일
├── playbg.png          배경 이미지
├── btn.png             패드 버튼 (기본 상태)
├── btn_.png            패드 버튼 (눌린 상태)
├── phantom.png         가이드 오버레이
├── phantom_.png        가이드 오버레이 변형 (선택)
├── custom_logo.png     커스텀 로고 (선택)
├── chainled.png        체인 LED 모드 이미지 (체인 모드 A)
├── chain.png           체인 기본 상태 (체인 모드 B)
├── chain_.png          체인 선택 상태 (체인 모드 B)
├── chain__.png         체인 가이드 상태 (체인 모드 B)
├── xml_prev.png        이전 버튼
├── xml_play.png        재생 버튼
├── xml_pause.png       일시정지 버튼
├── xml_next.png        다음 버튼
└── colors.json         UI 색상 커스터마이징 (선택)
```

## 필수 파일

### theme.json

테마의 이름, 제작자, 버전 정보를 담는 JSON 파일입니다.

```json
{
  "name": "My Theme",
  "author": "작성자 이름",
  "version": "1.0.0"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `name` | string | O | 테마 표시 이름 |
| `author` | string | O | 제작자 이름 |
| `version` | string | X | 버전 (기본값: `"1.0"`) |

### theme_ic.png

테마 선택 목록에 표시되는 아이콘입니다. 44dp 크기로 렌더링되며 둥근 모서리가 적용됩니다.

- 권장 크기: 132x132px (xxhdpi 기준)
- 정사각형 이미지 권장

## 이미지 리소스

모든 이미지 파일은 **PNG 형식**이어야 합니다. 제공하지 않은 리소스는 앱 기본 테마로 대체됩니다.

### 패드 영역

| 파일 | 설명 | 용도 |
|------|------|------|
| `playbg.png` | 연주 화면 전체 배경 | 패드 그리드 뒤에 표시 |
| `btn.png` | 패드 버튼 기본 상태 | 8x8 그리드의 각 패드 |
| `btn_.png` | 패드 버튼 눌린 상태 | 패드를 터치/눌렀을 때 |
| `phantom.png` | 가이드 오버레이 | 오토플레이 가이드 등에 사용 |
| `phantom_.png` | 가이드 오버레이 변형 | 짝수 크기 패드에서 2x2 패턴으로 교차 표시 (선택) |
| `custom_logo.png` | 커스텀 로고 | 연주 화면에 오버레이로 표시 (선택) |

### 체인 영역

체인은 두 가지 모드 중 하나를 선택합니다:

**모드 A: LED 모드** (`chainled.png` 존재 시)

| 파일 | 설명 |
|------|------|
| `chainled.png` | 체인 LED 이미지. LED 색상이 오버레이로 적용됨 |

이 모드에서는 체인 버튼이 패드 버튼(`btn.png`)을 배경으로 사용하고, `chainled.png`가 팬텀 레이어에 배치되어 LED 색상이 겹쳐 표시됩니다.

**모드 B: 드로어블 모드** (`chainled.png` 없을 시)

| 파일 | 설명 |
|------|------|
| `chain.png` | 체인 기본 상태 / LED 채널 |
| `chain_.png` | 체인 선택 상태 |
| `chain__.png` | 오토플레이 가이드 상태 |

이 모드에서는 각 상태별로 다른 이미지가 직접 표시됩니다.

### 재생 컨트롤

| 파일 | 설명 |
|------|------|
| `xml_prev.png` | 오토플레이 이전 버튼 |
| `xml_play.png` | 오토플레이 재생 버튼 |
| `xml_pause.png` | 오토플레이 일시정지 버튼 |
| `xml_next.png` | 오토플레이 다음 버튼 |

## 색상 커스터마이징 (선택)

### colors.json

UI 요소의 색상을 커스터마이징할 수 있습니다. 모든 필드는 선택사항이며, 지정하지 않으면 기본 색상이 사용됩니다.

```json
{
  "checkbox": "#FF5722",
  "trace_log": "#2196F3",
  "option_window": "#424242",
  "option_window_checkbox": "#FF9800"
}
```

| 필드 | 설명 |
|------|------|
| `checkbox` | 옵션 체크박스 색상 |
| `trace_log` | 트레이스 로그 텍스트 색상 |
| `option_window` | 옵션 패널 배경 색상 |
| `option_window_checkbox` | 옵션 패널 내 체크박스 색상 |

색상 값은 `#RRGGBB` 또는 `#AARRGGBB` 형식의 Hex 문자열입니다.

## 화면 구성 참고

```
┌──────────────────────────────────┐
│           playbg.png             │
│  ┌─────────────────────┐ ┌───┐  │
│  │  8x8 패드 그리드     │ │ C │  │
│  │  ┌───┬───┬───┬───┐  │ │ h │  │
│  │  │btn│btn│btn│btn│  │ │ a │  │
│  │  ├───┼───┼───┼───┤  │ │ i │  │
│  │  │btn│btn│btn│btn│  │ │ n │  │
│  │  ├───┼───┼───┼───┤  │ │   │  │
│  │  │btn│btn│btn│btn│  │ │   │  │
│  │  ├───┼───┼───┼───┤  │ │   │  │
│  │  │btn│btn│btn│btn│  │ │   │  │
│  │  └───┴───┴───┴───┘  │ └───┘  │
│  │   phantom 오버레이    │        │
│  └─────────────────────┘        │
│  ┌──────────────────────────┐   │
│  │ prev │ play/pause │ next │   │
│  └──────────────────────────┘   │
│         custom_logo.png         │
└──────────────────────────────────┘
```

## 예제

### 최소 구성 (필수 파일만)

```
minimal-theme.zip
├── theme.json
└── theme_ic.png
```

모든 리소스가 앱 기본 테마로 대체됩니다. 테마 목록에서 아이콘과 이름만 표시됩니다.

### 전체 구성

```
full-theme.zip
├── theme.json
├── theme_ic.png
├── colors.json
├── playbg.png
├── btn.png
├── btn_.png
├── phantom.png
├── phantom_.png
├── custom_logo.png
├── chainled.png
├── xml_prev.png
├── xml_play.png
├── xml_pause.png
└── xml_next.png
```

### theme.json 예제

```json
{
  "name": "Neon Glow",
  "author": "UniPad Community",
  "version": "2.1.0"
}
```

### colors.json 예제

```json
{
  "checkbox": "#00E5FF",
  "trace_log": "#76FF03",
  "option_window": "#1A1A2E",
  "option_window_checkbox": "#E94560"
}
```

## 설치 방법

1. 위 구조에 맞게 파일을 준비합니다.
2. 모든 파일을 하나의 ZIP 파일로 압축합니다.
   - 파일이 ZIP 루트에 바로 위치해야 합니다 (하위 폴더 안에 넣지 마세요).
3. UniPad 앱 → 테마 → 테마 추가 → **ZIP 파일 가져오기**를 선택합니다.
4. ZIP 파일을 선택하면 자동으로 검증 및 설치됩니다.

## 검증 규칙

임포트 시 다음 항목이 검증됩니다:

- `theme.json` 파일이 ZIP 루트에 존재해야 합니다.
- `theme_ic.png` 파일이 ZIP 루트에 존재해야 합니다.
- `theme.json`이 유효한 JSON이어야 하며, `name`과 `author` 필드가 필수입니다.

검증 실패 시 테마가 설치되지 않고 오류 메시지가 표시됩니다.

## 팁

- 이미지에 투명 배경(알파 채널)을 사용하면 LED 색상이 자연스럽게 겹쳐 표시됩니다.
- `btn.png`은 LED 색상이 위에 오버레이되므로, 어두운 톤의 이미지가 좋습니다.
- `phantom_.png`는 패드 크기가 짝수(예: 8x8)일 때 2x2 패턴으로 `phantom.png`과 번갈아 표시됩니다. 시각적 구분을 위해 약간 다른 디자인을 사용하세요.
- 체인 모드 A(LED 모드)는 하나의 이미지에 LED 색상이 동적으로 입혀지므로, 단일 이미지로 다양한 상태를 표현할 수 있습니다.
- 체인 모드 B(드로어블 모드)는 각 상태별 이미지를 직접 제어할 수 있어 더 자유로운 디자인이 가능합니다.
