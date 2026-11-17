#!/bin/bash
# 百度网盘修复包 - 打包脚本
# 在 Windows 上使用 Git Bash 或 WSL 运行

set -e

echo "================================"
echo "百度网盘修复包打包工具"
echo "================================"
echo ""

# 检查是否在正确的目录
if [ ! -f "CHANGELOG.md" ]; then
    echo "❌ 错误: 请在 qdl-fix 目录下运行此脚本"
    exit 1
fi

# 创建打包目录
PACK_DIR="qdl-fix-v037-debug-$(date +%Y%m%d)"
echo "创建打包目录: $PACK_DIR"
mkdir -p "$PACK_DIR"

# 复制必要文件
echo "复制文件..."
cp -r source "$PACK_DIR/"
cp BUILD_AND_TEST.md "$PACK_DIR/"
cp CHANGELOG.md "$PACK_DIR/"
cp test-baidu-flow.js "$PACK_DIR/"
cp ENVIRONMENT.md "$PACK_DIR/"
cp ISSUE.md "$PACK_DIR/"
cp README.md "$PACK_DIR/"

# 清理临时文件
echo "清理临时文件..."
rm -rf "$PACK_DIR/source/out"
rm -rf "$PACK_DIR/source/outdex"
rm -f "$PACK_DIR/source/app.unsigned.apk"
rm -f "$PACK_DIR/source/app-aligned.apk"

# 创建 zip
ZIPNAME="$PACK_DIR.zip"
echo "创建 ZIP: $ZIPNAME"
if command -v zip &> /dev/null; then
    zip -r "$ZIPNAME" "$PACK_DIR"
else
    echo "⚠️  zip 命令不可用,使用 7z"
    7z a "$ZIPNAME" "$PACK_DIR"
fi

# 清理
rm -rf "$PACK_DIR"

echo ""
echo "================================"
echo "✅ 打包完成!"
echo "================================"
echo "文件: $(pwd)/$ZIPNAME"
echo ""
echo "下一步:"
echo "1. 将 $ZIPNAME 传输到你的 proot Ubuntu 环境"
echo "2. 解压: unzip $ZIPNAME"
echo "3. 进入目录: cd $PACK_DIR"
echo "4. 阅读 BUILD_AND_TEST.md 了解构建和测试步骤"
echo ""
