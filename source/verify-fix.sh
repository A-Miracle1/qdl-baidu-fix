#!/bin/bash
# 百度网盘修复验证脚本
# 用于验证 APK 是否包含修复代码

echo "=== 验证修复代码 ==="
echo ""

# 检查源码中是否包含修复
if grep -q "savedBdClnd" src/com/dsh/qdl/MainActivity.java; then
    echo "✓ 源码包含 savedBdClnd 变量保护"
else
    echo "✗ 源码未包含修复代码"
    exit 1
fi

if grep -q "恢复为 verify 返回的值" src/com/dsh/qdl/MainActivity.java; then
    echo "✓ 源码包含 bdClnd 恢复逻辑"
else
    echo "✗ 源码未包含恢复逻辑"
    exit 1
fi

if grep -q "bdGetRaw 开始" src/com/dsh/qdl/MainActivity.java; then
    echo "✓ 源码包含增强的日志输出"
else
    echo "✗ 源码未包含增强日志"
    exit 1
fi

echo ""
echo "=== 源码验证通过 ==="
echo ""

# 如果存在 APK,检查修改时间
if [ -f "app.apk" ]; then
    echo "当前 APK 信息:"
    echo "  文件: app.apk"
    echo "  大小: $(ls -lh app.apk | awk '{print $5}')"
    echo "  修改时间: $(ls -l app.apk | awk '{print $6, $7, $8}')"
    echo ""
    echo "请确认 APK 是在源码修复后构建的"
fi

echo ""
echo "下一步:"
echo "1. 运行 sh build.sh 构建新版本"
echo "2. 将 app.apk 安装到测试设备"
echo "3. 使用测试链接: https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357"
echo "4. 检查日志应该显示 73999 字节和 locals=true"
