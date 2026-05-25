UBUNTU_DIR=$PREFIX/local/ubuntu
PROOT_BIN=$PREFIX/local/bin/proot

mkdir -p $UBUNTU_DIR

# Copy proot binary from native lib dir if not already present
[ ! -e "$PREFIX/files/proot" ] && [ -f "$NATIVE_LIB_DIR/libproot-loader.so" ] && cp "$NATIVE_LIB_DIR/libproot-loader.so" "$PREFIX/files/proot" && chmod +x "$PREFIX/files/proot"
[ ! -e "$PROOT_BIN" ] && [ -f "$PREFIX/files/proot" ] && cp "$PREFIX/files/proot" "$PROOT_BIN" && chmod +x "$PROOT_BIN"

if [ -z "$(ls -A "$UBUNTU_DIR" | grep -vE '^(root|tmp)$')" ]; then
    if [ -f "$PREFIX/files/custom-rootfs.tar.gz" ]; then
        echo "[*] Extracting custom rootfs..."
        tar -xf "$PREFIX/files/custom-rootfs.tar.gz" -C "$UBUNTU_DIR" 2>/dev/null || true
    elif [ -f "$PREFIX/files/ubuntu.tar.gz" ]; then
        echo "[*] Extracting Ubuntu rootfs..."
        tar -xf "$PREFIX/files/ubuntu.tar.gz" -C "$UBUNTU_DIR" 2>/dev/null || true
    fi
fi

ARGS="--kill-on-exit"
ARGS="$ARGS -w /"

for system_mnt in /apex /odm /product /system /system_ext /vendor; do
    if [ -e "$system_mnt" ]; then
        system_mnt=$(realpath "$system_mnt" 2>/dev/null || echo "$system_mnt")
        ARGS="$ARGS -b ${system_mnt}"
    fi
done

ARGS="$ARGS -b /sdcard"
ARGS="$ARGS -b /storage"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /data"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b /sys"
ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b /dev/urandom:/dev/random"

if [ -e "/proc/self/fd" ]; then ARGS="$ARGS -b /proc/self/fd:/dev/fd"; fi
if [ -e "/proc/self/fd/0" ]; then ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"; fi
if [ -e "/proc/self/fd/1" ]; then ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"; fi
if [ -e "/proc/self/fd/2" ]; then ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"; fi

if [ -e "$PREFIX/local/stat" ]; then ARGS="$ARGS -b $PREFIX/local/stat:/proc/stat"; fi
if [ -e "$PREFIX/local/vmstat" ]; then ARGS="$ARGS -b $PREFIX/local/vmstat:/proc/vmstat"; fi

if [ ! -d "$UBUNTU_DIR/tmp" ]; then
    mkdir -p "$UBUNTU_DIR/tmp"
    chmod 1777 "$UBUNTU_DIR/tmp"
fi
ARGS="$ARGS -b $UBUNTU_DIR/tmp:/dev/shm"

ARGS="$ARGS -r $UBUNTU_DIR"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

export PROOT_TMP_DIR=${PROOT_TMP_DIR:-$UBUNTU_DIR/tmp}

$LINKER $PROOT_BIN $ARGS /bin/sh $PREFIX/local/bin/init "$@"
