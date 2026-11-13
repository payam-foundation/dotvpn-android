# Needs: Go (same version as ../dotvpn-client/go.mod), gomobile, JDK 17,
# ANDROID_HOME with platform 37 + build-tools, ANDROID_NDK_HOME (r28+ preferred; r27+ ok).
CLIENT ?= ../dotvpn-client
AAR    := app/libs/dotvpn.aar
# 16 KB ELF LOAD alignment for Android 15+ devices. Safe on older 4 KB devices too.
# gomobile often ignores NDK r28's default, so force the linker flag (golang/go#74839).
CGO_ANDROID_PAGEFLAGS := -Wl,-z,max-page-size=16384

.PHONY: aar apk release test vet clean check-16kb
aar:
	mkdir -p $(dir $(AAR))
	cd $(CLIENT) && CGO_LDFLAGS="$$(go env CGO_LDFLAGS) $(CGO_ANDROID_PAGEFLAGS)" \
		gomobile bind -target=android/arm64,android/amd64 -androidapi 26 \
		-trimpath -ldflags="-s -w -checklinkname=0" -o $(CURDIR)/$(AAR) ./mobile

# Fail if any 64-bit JNI .so in the AAR has LOAD align < 16 KB (2**14).
check-16kb: $(AAR)
	@tmpdir=$$(mktemp -d) && trap 'rm -rf "$$tmpdir"' EXIT && \
	unzip -q -o $(AAR) 'jni/arm64-v8a/*.so' 'jni/x86_64/*.so' -d "$$tmpdir" && \
	objdump=$$(ls \
		$${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/*/bin/llvm-objdump \
		$${ANDROID_NDK_HOME}/*/toolchains/llvm/prebuilt/*/bin/llvm-objdump \
		$${ANDROID_HOME}/ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-objdump \
		2>/dev/null | head -1) && \
	test -n "$$objdump" || { echo "llvm-objdump not found; set ANDROID_NDK_HOME or ANDROID_HOME"; exit 1; } && \
	bad=0; \
	for so in "$$tmpdir"/jni/*/*.so; do \
		min=$$("$$objdump" -p "$$so" | sed -n '/LOAD/s/.*align 2\*\*\([0-9][0-9]*\).*/\1/p' | sort -n | head -1); \
		echo "$$so: min align 2**$${min:-?}"; \
		if [ -z "$$min" ] || [ "$$min" -lt 14 ]; then echo "  UNALIGNED (<16KB)"; bad=1; fi; \
	done; \
	test "$$bad" -eq 0

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
