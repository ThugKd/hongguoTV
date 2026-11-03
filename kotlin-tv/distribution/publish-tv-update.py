#!/usr/bin/env python3
"""Runs only in the public source repository after release validation succeeds."""
import hashlib, json, os, re, shutil, subprocess
from pathlib import Path
root=Path.cwd(); out=root/'outputs'; out.mkdir(exist_ok=True)
def run(*args): return subprocess.check_output(args,text=True).strip()
version=os.environ['LVGUOTV_VERSION_NAME']; code=int(os.environ['LVGUOTV_VERSION_CODE']); tag=os.environ['UPDATE_TAG']
assert re.fullmatch(r'0\.9\.2\+\d+',version) and re.fullmatch(r'kotlin-v0\.9\.2-build\.\d+',tag)
assert code==10000+int(version.split('+')[1])
apk=out/f'lvguotv-kotlin-{tag.removeprefix("kotlin-v")}-android6.apk'; shutil.copy2(root/'kotlin-tv/app/build/outputs/apk/release/app-release.apk',apk)
buildtools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
signature=run(str(buildtools/'apksigner'),'verify','--print-certs',str(apk))
# Original release key was lost; channel re-keyed 2026-10. Installed builds must be reinstalled once.
assert '35f31788e416d7f6f690b595cba379697803a7e0f7626f7ea69677adac231bf5' in signature
badging=run(str(buildtools/'aapt'),'dump','badging',str(apk))
assert f"versionCode='{code}'" in badging and f"versionName='{version}'" in badging
sdk_match=re.search(r"^sdkVersion:'(\d+)'$",badging,re.MULTILINE)
assert sdk_match is not None and int(sdk_match[1])>=23
min_sdk=int(sdk_match[1])
commit=run('git','rev-parse','HEAD')
manifest={'schema':1,'packageName':'com.thugkd.lvguotv','versionCode':code,'versionName':version,'minSdk':min_sdk,'tag':tag,
 'sourceCommit':commit,'apk':{'name':apk.name,'size':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()},
 'notes':'应用内自动检查和下载更新；保留安静连播、完整竖版海报和本机记录。'}
(out/'update.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
source=out/f'lvguotv-{tag}-source.zip'; run('git','archive','--format=zip',f'--prefix=lvguotv-{tag}/','--output',str(source),'HEAD')
notes=out/'release-notes.md'; notes.write_text(f'''Android API {min_sdk}+ 原生独立版（当前最低 Android 6.0），版本 {version}（versionCode {code}）。

自动检查、下载新版并校验签名，确认后由系统安装。旧版更新器限制最低 API 26，首次升级本版需手动覆盖安装，之后可正常应用内更新。电视需允许未知来源安装，Android 8 起按应用授权；每次更新仍需确认系统安装。

保留安静连播、完整竖版海报和所有本机记录。使用原发布签名，可直接覆盖此前正式版。

自动构建的源码提交：{commit}。测试与 Release lint 通过后才发布。

- APK：电视安装文件。
- source.zip：与 APK 对应的完整原生源码，GPL-3.0。
- update.json：电视更新元数据，含版本、文件长度与 SHA-256。
- SHA256SUMS：附件校验和。

复现发布构建时设置 LVGUOTV_VERSION_CODE={code} 和 LVGUOTV_VERSION_NAME={version}，并使用自己的签名配置。Android 6.0 实体电视兼容性需实机确认。
''')
checks=out/'SHA256SUMS'; checks.write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [apk,source,out/'update.json']))
# Rerunning a published build must not overwrite the APK installed on TVs.
existing=subprocess.run(['gh','release','view',tag,'--json','isDraft'],text=True,capture_output=True)
if existing.returncode==0:
 if not json.loads(existing.stdout)['isDraft']:
  print('This build is already published; leaving its assets unchanged.'); raise SystemExit(0)
else: run('gh','release','create',tag,'--target',commit,'--draft','--title',f'绿果TV {version}','--notes-file',str(notes))
assets=[apk,source,out/'update.json',checks]
run('gh','release','upload',tag,*map(str,assets),'--clobber')
info=json.loads(run('gh','release','view',tag,'--json','assets'))
for p in assets:
 a=next(a for a in info['assets'] if a['name']==p.name)
 assert a['state']=='uploaded' and a['size']==p.stat().st_size and a['digest']=='sha256:'+hashlib.sha256(p.read_bytes()).hexdigest()
# Concurrent older builds must never replace a newer release as latest.
latest=subprocess.run(['gh','release','view','--json','tagName'],text=True,capture_output=True)
make_latest=True
if latest.returncode==0:
 m=re.fullmatch(r'kotlin-v0\.9\.2-build\.(\d+)',json.loads(latest.stdout)['tagName'])
 if m and int(m[1])>int(version.split('+')[1]): make_latest=False
run('gh','release','edit',tag,'--draft=false','--latest='+str(make_latest).lower(),'--notes-file',str(notes))
print(json.dumps({'tag':tag,'version_code':code,'sha256':manifest['apk']['sha256']}))
