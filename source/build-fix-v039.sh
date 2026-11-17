#!/bin/bash
# 百度网盘修复版本构建脚本
# 使用方法: 将此脚本和修改后的 MainActivity.java 上传到手机 proot 环境

echo "=== 百度网盘修复版本 v0.39 构建 ==="
echo "修复内容: 保护 bdClnd 变量,防止被 bdToken() 覆盖"
echo ""

cd "$(dirname "$0")"

# 检查是否在正确的目录
if [ ! -f "build.sh" ]; then
    echo "错误: 请在 source 目录下运行此脚本"
    exit 1
fi

# 备份原文件
if [ -f "src/com/dsh/qdl/MainActivity.java.bak" ]; then
    echo "发现备份文件,是否使用修复版本? (y/n)"
    read -r answer
    if [ "$answer" != "y" ]; then
        echo "取消构建"
        exit 0
    fi
fi

# 构建
echo "开始构建..."
sh build.sh

if [ $? -eq 0 ]; then
    echo ""
    echo "=== 构建成功 ==="
    echo "APK 位置: $(pwd)/app.apk"
    echo "文件大小: $(ls -lh app.apk | awk '{print $5}')"
    echo ""
    echo "下一步:"
    echo "1. 将 app.apk 复制到 /sdcard/Download/DSH-Go/"
    echo "2. 在手机上安装 (覆盖安装)"
    echo "3. 使用测试链接验证"
    echo "4. 检查日志: /sdcard/Download/DSH-Go/qdl.log"
else
    echo ""
    echo "=== 构建失败 ==="
    exit 1
fi
