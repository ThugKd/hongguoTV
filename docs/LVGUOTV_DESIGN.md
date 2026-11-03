# 绿果TV 1.0.0 视觉设计

以已确认的绿色首页概念图为基准。范围仅为现有页面、组件和品牌资源的视觉调整，保留原有数据、播放、片源、收藏、备份和权限流程。

## 统一规范

- 默认主题：黑色影院。背景 `#080808`，卡片 `#171717`，抬高层 `#242424`，主色 `#8FD0AC`，主文字 `#F3F3F3`，次文字 `#A8A8A8`。绿色仅用于主按钮、选择、进度与焦点，不再铺设绿色页面背景。原有浅色主题使用中性灰白。品牌图标保留翡翠绿识别色。
- 最终布局验收以 16:9 电视画幅为准，1080p / 320dpi 对应 960×540dp。21:9 手机用于安装、播放与按键测试，同时验证窄高比窗口的基本可用性。
- 标题使用系统中文中黑体，主标题 24–30sp，分区 16–18sp，正文 14–16sp，辅助信息至少 12sp。较小屏幕按可用高度收紧间距。
- 四周保留电视安全区。左侧内容导航放推荐、片库与榜单；短剧和漫剧作为下方固定筛选横条中的内容类型切换，沿用现有接口。右侧独立工具区放搜索、收藏、历史、设置，宽窗口采用图标加文字，窄窗口使用图标。片源入口放在设置中。剧集卡片圆角 8dp，大图区圆角 12dp，弹窗圆角 16dp；取消过大的胶囊轮廓与整片白色焦点。
- 当前分类：绿色文字与细下划线；遥控器焦点：绿色描边；主要播放按钮：绿色填充与深色文字。保持焦点移动时卡片尺寸稳定。
- 片名、海报、进度和集数使用真实接口或本机数据；设计概念图中的影视人物与示例剧名不写入客户端。
- 所有剧集卡片沿用首页样式：封面宽高比 0.76（六列保持不变，允许适量居中裁剪）、封面与卡片零内边距、8dp 圆角；透明卡片底，片名置于图下方（13sp、双行省略），11sp 集数/状态置于封面底部渐变层。焦点用前景绿色描边与绿色片名，防止贴边封面遮挡焦点。历史卡片的观看文字与进度条统一置于封面底部渐变层：文字左对齐，进度条高 2dp、左右内收 8dp、距封面底部 9dp，圆角端点；不占用片名区域。榜单继续使用左上数字角标。

## 页面布局

| 页面 | 布局和层次 | 主要交互 |
| --- | --- | --- |
| 首页 | 顶部品牌、内容导航和工具区；大幅推荐卡与右侧继续观看（无历史时使用真实推荐）；下方六列纵向网格；导航下方使用轻量标题与操作横条；短剧 / 漫剧为横条内的二级类型切换，题材、随机荐剧与刷新固定在横条右侧 | 推荐进入现有详情/播放流程；右侧使用现有历史记录，无记录时提示空态；保留长按管理与刷新；全部已加载推荐均在当前页面展示，向下滚动追加下一批，保留焦点与滚动位置；菜单键在固定分类栏与当前卡片之间切换，不滚回顶部 |
| 搜索 | 统一输入框；键盘和历史为左侧面板，联想与结果留足横向空间；筛选条件沿用现有面板 | 保留首拼、手机输入、高级搜索、联想与分页；不增加搜索方式；高级搜索进入结果时收起条件组，第一行向上可回到展开筛选按钮 |
| 榜单 | 导航下方统一工具栏、题材/榜单切换、对齐的海报网格 | 沿用榜单和 AI 分类；卡片沿用首页样式，排名使用贴合海报左上角的两位数字角标（01、02…），左上 8dp / 右下 6dp 圆角、24dp 高、13sp 数字，前三名薄荷绿底，其余浅灰绿 #B7C7BD；热度独立放在片名下方，使用暖金色 #E7C485 和火焰图标，保留完整名次无障碍说明 |
| 收藏/历史 | 分区标题与分组/更新/管理工具栏；海报比例一致，进度与说明对齐 | 现有收藏分组、更新检查、继续观看和记录管理 |
| 详情 | 炭灰信息卡；左侧完整海报，右侧片名、元数据、简介与主次操作；下方季选择、集数组和选集网格 | 播放按钮为唯一绿色主操作；保留简介展开、收藏、跳集和相关剧 |
| 播放 | 画面全屏；底部黑色渐变控制区；绿色进度条、焦点及选集状态；绿果TV 水印位于实际视频画幅右上角，内收 12dp、透明度 48%，使用透明底图标；首帧后等待画幅布局完成再显示，切集时隐藏，不获取焦点 | 保留原播放器手势/按键、倍速、清晰度、画面、弹幕、定时与跳片头尾 |
| 设置 | 播放偏好与应用/内容两列，片源管理位于应用/内容首项；统一面板标题、行间距、值与焦点；固定品牌版本页脚 | 所有设置项及保存行为保留 |
| 片源 | 由设置进入，返回回到设置；统一工具栏、输入框、源列表、网格与详情；空态明确说明用户自行添加 | 不内置片源，不增加推荐源或自动添加 |
| 手机快传/备份/背景 | 电视二维码卡与列表使用统一主题；手机已有页面同步绿色按钮、背景和品牌名 | 保留上传、下载、预览、删除与电视确认流程 |
| 通用弹窗/错误/空态 | 炭灰实色面板、清楚的标题和正文、矩形圆角按钮；绿色焦点 | 原有确认顺序、取消和返回焦点保持 |

## 本轮细节与参考

- 16:9 推荐大图区 168dp；高度较小的窗口使用 176dp，并收紧右侧缩略图/行间距。继续观看每条最多两行片名，独立保留集数、时间和进度条，避免 21:9 下堆叠溢出。
- 时间功能保留，只在目录页导航中显示，分配独立的 58dp 槽位；详情和播放器不悬浮显示时间，不覆盖设置或水印。
- 返回箭头改用 20dp 原生矢量，实际箭头图形高度与 15sp 中文按钮相近，焦点颜色跟随文字。
- 仅焦点卡片使用 6dp 原生阴影，大图区 3dp、固定分类栏 4dp、弹窗 12dp。保留描边，不使用大面积模糊或位图阴影。
- 借鉴 [Netflix 顶部导航与始终可见入口](https://www.netflix.com/tudum/articles/netflix-new-tv-layout) 的可达性思路，以及 [Android TV 焦点系统](https://developer.android.com/design/ui/tv/guides/styles/focus-system) 对描边、颜色与阴影的建议。保留本项目黑底、薄荷绿、竖版短剧内容与原有功能，不复制其页面或推荐算法。

## 品牌资源

使用翡翠绿果实与负形播放三角构成应用标记，深墨绿背景。应用内标记、启动器图标和电视横幅保持一致；名称为“绿果TV”。applicationId 和 namespace 均为 `com.thugkd.lvguotv`，版本 `1.0.0`。

资源通过内置 imagegen 生成，原图保存于 `docs/design/lvguotv/launcher-source.png`、`docs/design/lvguotv/tv-banner-source.png`。Android 各密度启动图与 320×180 电视横幅由原图等比缩放，应用内标记使用原生矢量资源。

应用包与核心模块源码分别位于 `com.thugkd.lvguotv` 和 `com.thugkd.lvguotv.core`。界面、手机页面、更新元数据和 APK 文件名统一使用绿果命名。上游 GitHub 项目署名、内容平台参数及旧备份格式/存储目录保留兼容标识。

图标生成提示词：

> Use case: logo-brand. Production Android launcher icon for the Chinese TV streaming app 绿果TV, matching the approved forest-green and jade-green interface. ONE flat square 1024 by 1024 icon, full-bleed solid deep forest green #0B1712 background with no rounded outer canvas corners. Center a simple distinctive jade green #58D59B round fruit/apple silhouette, with a small stem and two tiny elegantly angled leaves, and a dark forest-green right-pointing play triangle cut out of the center. Mark fits inside the middle 64 percent of the square with generous Android safe padding. Bold clean flat geometric vector-like edges, optically centered, recognizable at 48px. No text or lettering, no gradients, no gloss, no shadows, no frame, no 3D, no extra symbols, no multiple icon variants.

横幅生成提示词（参考图为上述图标）：

> Use case: logo-brand. Create a production Android TV launcher banner, 16:9 landscape, 1280x720 or 1920x1080, using the provided fruit/play icon as the reference for the exact mark. Flat full-bleed solid deep forest green #0B1712 background. Place a compact jade green #58D59B fruit/play mark to the left of the exact text “绿果TV”, centered together as a balanced horizontal wordmark with plenty of safe margins. Clean modern Chinese sans-serif, medium bold, off-white #F1F7F2 lettering. Match the fruit silhouette and negative play triangle of the reference, redraw crisply at this size. Minimal, quiet, readable even when the banner is reduced to 320x180. No other text, no slogans, no visual effects, no frame, no shadows, no gradients, no 3D. This is the banner itself, not a device presentation.

本次更名使用内置 imagegen，仅替换既有横幅的第一个品牌字为“绿”，保留标记、字重、位置、底色与 16:9 画幅。最终可见字样为“绿果TV”。

- 题材选择弹窗记住当前选择与列表位置，显式连接列表和底部按钮焦点；取消返回原入口。
- 内容网格聚焦时保证整张卡片进入可视区域；菜单返回直接恢复原卡片，频道滚动记录按内容类型隔离。

- 随机荐剧每批最多 9 部，使用三列三行的紧凑横向卡片：封面贴左，标题单行省略，保留完整无障碍名称；卡片宽度由行权重分配，弹窗内容高度随窗口自适应。方向键在九宫格与换一批 / 关闭之间显式连接，关闭回到原入口。
- 播放控制面板使用从顶部全透明、向底部逐渐加深的黑色渐变；主播放器与三方播放器底部面板使用同一渐变。

- 窗口高度小于 450dp 时，网格页省略额外的选中剧集预览区，为封面与片名保留空间；历史卡片内使用简短的“第 N 集 · 时间”，详细观看状态保留在详情中。

- 首页焦点按实际区域与横向位置衔接：顶栏与筛选栏就近移动；大图播放 / 收藏分别进入下方推荐第一 / 第二列；继续观看末条向下进入推荐末列；首排卡片向上返回对应大图或继续观看区域。
- 推荐网格左右在行边界停止，上下保持列号；菜单键在固定题材入口与原卡片之间切换。进入详情、弹窗取消和异步刷新后恢复对应控件与滚动位置。
- 页尾加载新一批推荐后，焦点进入新增内容第一张，避免直接跳到新的页尾。当前类型使用无描边的浅绿选中态，与实际焦点区分。

- 观看状态分为继续观看、已追平、已看完。当前末集完成后移出接着看；重新进入先展示状态，重播由用户主动选择。仅在内容标记明确时认定完结，未知时显示已追平；更新出现新集后恢复续播。
- 搜索在当前应用会话内分别记忆关键词/草稿、结果、分页和高级筛选；关键词搜索与高级搜索独立保存。推荐/片库以及各自短剧/漫剧独立记忆题材、列表、焦点与滚动位置。
- 分页每 10 页形成一段，已有内容保留，确认继续下一段后追加；100 页为当前接口浏览边界，明确提示上限，不提示全部。失败后保留内容和页码，提供确认重试按钮；搜索会话过期由用户确认刷新。
- 两类播放器全屏确认均切换播放/暂停，左右按设置步长定位，长按右键按设置倍速临时快进；控制区内方向键仍用于导航。返回先收起面板，再退出。
- 随机荐剧明确标注全站范围（短剧/漫剧）；退出仅保留继续使用和退出两个操作，默认焦点在继续使用。
