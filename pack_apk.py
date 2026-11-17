# -*- coding: utf-8 -*-
import zipfile
import os
import sys
import shutil

apk_dir = 'apk-extracted'
output_apk = 'build-temp/网盘直链下载-v1.0.1-unsigned.apk'

# 复制修改后的文件
shutil.copy('source/assets/index.html', 'apk-extracted/assets/index.html')
print('   [OK] Updated index.html')

# 打包
with zipfile.ZipFile(output_apk, 'w', zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, files in os.walk(apk_dir):
        if 'META-INF' in root:
            continue
        for file in files:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, apk_dir)
            if file.endswith(('.so', '.png', '.jpg', '.jpeg')):
                zipf.write(file_path, arcname, zipfile.ZIP_STORED)
            else:
                zipf.write(file_path, arcname, zipfile.ZIP_DEFLATED)

size = os.path.getsize(output_apk) / 1024
print('   [OK] APK packed: {:.1f} KB'.format(size))
