PACKAGE = de.markusfisch.android.screentime

all: debug install start

debug:
	./gradlew assembleDebug

lint:
	./gradlew lintDebug

release: lint
	./gradlew assembleRelease

bundle: lint
	./gradlew bundleRelease

install:
	adb $(TARGET) install -r app/build/outputs/apk/debug/app-debug.apk

start:
	adb $(TARGET) shell 'am start -n $(PACKAGE)/.activity.MainActivity'

uninstall:
	adb $(TARGET) uninstall $(PACKAGE)

avocado:
	avocado $(shell fgrep -rl '<vector' app/src/main/res)

clean:
	./gradlew clean
