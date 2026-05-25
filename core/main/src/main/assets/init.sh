set -e

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export HOME=/root
export TERM=xterm-256color
export LANG=C.UTF-8

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
    echo "nameserver 8.8.4.4" >> /etc/resolv.conf
fi

export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@reterm \[\033[39m\]\w \[\033[0m\]\\$ "
export PIP_BREAK_SYSTEM_PACKAGES=1

# Auto-detect package manager
if command -v apt-get >/dev/null 2>&1; then
    # Debian/Ubuntu
    if [ ! -f /etc/apt/sources.list ] || ! grep -q 'ubuntu\|debian' /etc/apt/sources.list 2>/dev/null; then
        printf "\033[34;1m[*] \033[0mConfiguring apt sources\033[0m\n"
        cat > /etc/apt/sources.list <<EOF
deb http://ports.ubuntu.com/ubuntu-ports noble main restricted universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports noble-updates main restricted universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports noble-security main restricted universe multiverse
EOF
    fi
    missing=""
    command -v bash >/dev/null 2>&1 || missing="$missing bash"
    command -v nano >/dev/null 2>&1 || missing="$missing nano"
    if [ -n "$missing" ]; then
        printf "\033[34;1m[*] \033[0mInstalling packages\033[0m\n"
        apt-get update -y -qq && apt-get upgrade -y -qq
        apt-get install -y -qq ca-certificates $missing
        printf "\033[32;1m[+] \033[0mDone\033[0m\n"
    fi
elif command -v apk >/dev/null 2>&1; then
    # Alpine
    if [ ! -f /etc/apk/repositories ] || ! grep -q community /etc/apk/repositories 2>/dev/null; then
        printf "\033[34;1m[*] \033[0mConfiguring apk repositories\033[0m\n"
        cat > /etc/apk/repositories <<EOF
https://dl-cdn.alpinelinux.org/alpine/latest-stable/main
https://dl-cdn.alpinelinux.org/alpine/latest-stable/community
EOF
    fi
    if ! command -v bash >/dev/null 2>&1; then
        printf "\033[34;1m[*] \033[0mInstalling packages\033[0m\n"
        apk update && apk upgrade
        apk add bash nano
        printf "\033[32;1m[+] \033[0mDone\033[0m\n"
    fi
elif command -v dnf >/dev/null 2>&1; then
    if ! command -v bash >/dev/null 2>&1; then
        dnf update -y -q && dnf install -y -q bash nano
        printf "\033[32;1m[+] \033[0mDone\033[0m\n"
    fi
elif command -v pacman >/dev/null 2>&1; then
    if ! command -v bash >/dev/null 2>&1; then
        pacman -Syu --noconfirm && pacman -S --noconfirm bash nano
        printf "\033[32;1m[+] \033[0mDone\033[0m\n"
    fi
else
    printf "\033[33;1m[!] \033[0mUnknown rootfs\033[0m\n"
fi

if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    [ -f /etc/profile ] && . /etc/profile
    export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@reterm \[\033[39m\]\w \[\033[0m\]\\$ "
    cd $HOME
    command -v bash >/dev/null 2>&1 && exec /bin/bash || exec /bin/sh
else
    exec "$@"
fi
