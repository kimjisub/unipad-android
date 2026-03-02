# UniPad Android 프로젝트

<a href='https://play.google.com/store/apps/details?id=com.kimjisub.launchpad&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' height="100px" src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a>

[English](README.md) | **한국어**

## 개요

UniPad는 런치패드와 연결하여 연주할 수 있는 퍼포먼스 기반 리듬 게임으로, 독자적인 "유니팩(unipack)" 포맷을 사용하여 사용자가 직접 비트맵을 제작하고 커뮤니티 내에서 창의성과 공유를 촉진합니다.

## 주요 기능

- 커스텀 비트맵 제작: 다양한 곡에 대한 고유한 비트맵과 리듬을 디자인할 수 있습니다.
- 비트맵 공유: 커뮤니티 내 다른 사용자들과 작품을 공유할 수 있습니다.
- 커뮤니티 비트맵 다운로드: 다른 사용자들이 만든 다양한 비트맵 라이브러리에 접근할 수 있습니다.
- 인앱 음악 라이브러리: 앱에서 제공하는 퍼블릭 도메인 라이선스 음악 라이브러리를 활용할 수 있습니다.
- 정기적인 업데이트 및 버그 수정: 최신 기능과 개선 사항을 지속적으로 제공합니다.

## 시작하기

### 사전 요구사항

이 프로젝트를 로컬에서 실행하려면 다음 소프트웨어가 설치되어 있어야 합니다:

1. Android Studio (최신 버전)
2. Android SDK (API 29-35, Android 10+)
3. Git (저장소 클론용)
4. Java Development Kit (JDK) - Android Studio의 번들 JDK 권장

### 설치 방법

로컬 컴퓨터에 이 프로젝트를 설정하려면 다음 단계를 따르세요:

1. 저장소 클론:
```bash
git clone https://github.com/kimjisub/unipad-android.git
cd unipad-android
```

2. Android Studio에서 프로젝트를 열고 Gradle 파일이 동기화되도록 합니다.

3. 동기화가 완료되면 프로젝트를 빌드하고 Android 기기 또는 에뮬레이터에서 실행합니다.

## 프로젝트 빌드

### 개발 빌드

```bash
# Windows: Android Studio의 번들 JDK로 JAVA_HOME 설정
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"

# 클린 빌드
./gradlew clean

# 디버그 APK 빌드
./gradlew assembleDebug
```

빌드된 APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

### 릴리즈 빌드

```bash
# 릴리즈 APK 빌드 (keystore.properties 필요)
./gradlew assembleRelease
```

**참고:** 릴리즈 빌드를 위해서는 프로젝트 루트에 서명 정보가 포함된 `keystore.properties` 파일을 생성해야 합니다:
```properties
storeFile=path/to/your/keystore.jks
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

## 테스트

환경 설정, 테스트 실행, 여러 API 레벨 테스트, 문제 해결을 포함한 종합 테스트 문서:

**📖 [테스트 가이드](docs/TESTING.ko.md)**

## 프로젝트 구조

- **UniPack System**: 사운드 테이블, LED 애니메이션, 자동 재생 시퀀스가 포함된 커스텀 비트맵 포맷
- **MIDI Connection**: 다양한 런치패드 모델(MK2, PRO, X, MK3, S)을 지원하는 USB MIDI 통신
- **Runners**: 사운드 재생, LED 애니메이션, 자동 재생 시퀀스를 위한 백그라운드 프로세서
- **Database**: 유니팩 메타데이터 관리를 위한 Room 데이터베이스
- **Architecture**: Koin 의존성 주입을 사용한 MVVM 아키텍처

자세한 기술 문서는 [CLAUDE.md](CLAUDE.md)를 참조하세요.

## 기여하기

이 프로젝트에 대한 기여를 환영합니다! 기여를 원하시면 다음 단계를 따라주세요:

1. 저장소를 포크합니다.
2. 설명이 포함된 새 브랜치를 생성합니다 (예: `feat/new-functionality` 또는 `fix/bug`).
3. 새 브랜치에 변경 사항을 커밋합니다.
4. 포크한 저장소에 변경 사항을 푸시합니다.
5. 메인 저장소에 새 풀 리퀘스트를 생성합니다.

## 라이선스

이 프로젝트는 [GNU Lesser General Public License v2.1](LICENSE.md) 라이선스를 따릅니다. 자세한 내용은 `LICENSE.md` 파일을 참조하세요.

## 감사의 글

- 이 오픈소스 프로젝트에 영감을 준 원작 UniPad 앱에 감사드립니다.
- 이 프로젝트를 가능하게 만든 모든 기여자와 사용자들에게 감사드립니다.
- 질문이나 추가 정보가 필요하시면 0226daniel@gmail.com으로 연락주시기 바랍니다.
