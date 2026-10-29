# Needs: Go (same version as ../dotvpn-client/go.mod), gomobile, JDK 17,
# ANDROID_HOME with platform 35 + build-tools, ANDROID_NDK_HOME (r26+).
CLIENT ?= ../dotvpn-client
AAR    := app/libs/dotvpn.aar

.PHONY: aar apk release test vet clean
aar:
	mkdir -p $(dir $(AAR))
	cd $(CLIENT) && gomobile bind -target=android/arm64,android/amd64 -androidapi 26 \
		-trimpath -ldflags="-checklinkname=0" -o $(CURDIR)/$(AAR) ./mobile

# Go side checks that do not need the SDK.
vet:
	cd $(CLIENT) && GOOS=android GOARCH=arm64 go vet ./... && go test -race -count=1 ./mobile

test: aar
	./gradlew --no-daemon testDebugUnitTest

apk: aar
	./gradlew --no-daemon assembleDebug

# Signed with DOTVPN_KEYSTORE* from the environment (CI secrets); unsigned without.
release: aar
	./gradlew --no-daemon assembleRelease

clean:
	rm -f $(AAR) $(AAR:.aar=-sources.jar)
	./gradlew --no-daemon clean
