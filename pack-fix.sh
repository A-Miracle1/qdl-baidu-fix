#!/bin/bash
# 打包修复版本
# 将所有修复相关的文件整理到一个目录

set -e
cd "$(dirname "$0")"

VERSION="v039"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
PACKAGE_NAME="qdl-baidu-fix-${VERSION}-${TIMESTAMP}"

echo "=== 打包百度网盘修复版本 ==="
echo "版本: $VERSION"
echo "时间: $TIMESTAMP"
echo ""

# 创建打包目录
mkdir -p "$PACKAGE_NAME"

# 复制文档
echo "复制文档..."
cp -v 百度网盘修复完成-v039.md "$PACKAGE_NAME/"
cp -v 代码修改对比-v039.md "$PACKAGE_NAME/"
cp -v 百度网盘修复说明-v039.md "$PACKAGE_NAME/"
cp -v 网盘直链下载-使用指南.md "$PACKAGE_NAME/"
cp -v README-修复版本.md "$PACKAGE_NAME/README.md"

# 复制源码
echo "复制源码..."
mkdir -p "$PACKAGE_NAME/source"
cp -rv source/src "$PACKAGE_NAME/source/"
cp -v source/build.sh "$PACKAGE_NAME/source/"
cp -v source/build-fix-v039.sh "$PACKAGE_NAME/source/"
cp -v source/verify-fix.sh "$PACKAGE_NAME/source/"
cp -v source/AndroidManifest.xml "$PACKAGE_NAME/source/"
cp -rv source/res "$PACKAGE_NAME/source/"
cp -rv source/assets "$PACKAGE_NAME/source/"
cp -v source/qdl.keystore "$PACKAGE_NAME/source/"

# 复制测试脚本
echo "复制测试脚本..."
mkdir -p "$PACKAGE_NAME/tools"
cp -v tools/baidu-cookie-test.js "$PACKAGE_NAME/tools/"

# 如果存在 APK,也复制
if [ -f "source/app.apk" ]; then
    echo "复制 APK..."
    cp -v source/app.apk "$PACKAGE_NAME/网盘直链下载-${VERSION}-beta.apk"
fi

# 创建 tar.gz 压缩包
echo ""
echo "创建压缩包..."
tar -czf "${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME"

# 显示结果
echo ""
echo "=== 打包完成 ==="
echo "目录: $PACKAGE_NAME"
echo "压缩包: ${PACKAGE_NAME}.tar.gz"
echo "大小: $(du -h ${PACKAGE_NAME}.tar.gz | awk '{print $1}')"
echo ""
echo "可以将压缩包传输到 proot Ubuntu 环境:"
echo "1. 解压: tar -xzf ${PACKAGE_NAME}.tar.gz"
echo "2. 进入: cd ${PACKAGE_NAME}/source"
echo "3. 构建: sh build.sh"
