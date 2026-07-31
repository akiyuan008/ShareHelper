# 分享助手 (ShareHelper)

一个轻量级的 Android 分享中转工具，作为系统分享面板的增强替代方案，提供更强大的分享体验。

## 功能特性

### 核心功能
- **智能分享中转** — 接收来自任意应用的分享内容，展示完整的接收应用列表
- **双路径加载** — 快速路径秒级显示结果，后台线程完整枚举所有可用目标
- **内容预览** — 显示分享来源应用名称和内容摘要（文本、文件、图片等）
- **系统分享器回退** — 可一键跳转到原生系统分享面板

### 分组管理
- 精确到每个分享入口（pkg/cls）进行分组
- 分享面板中通过标签栏快速切换分组筛选
- 支持创建、重命名、删除分组
- 长按应用可快速添加/移除分组

### 排序功能
- **A-Z 字母排序** — 按应用名首字母排列
- **按使用频率排序** — 根据历史使用次数智能排序，常用应用自动靠前
- 排序模式自动记忆

### 搜索功能
- 支持模糊搜索，匹配分享项名称、应用名称和包名
- 子序列匹配：输入字符按顺序出现即可匹配
- 词边界匹配：匹配任意单词开头

### 置顶功能
- 精确到具体分享项（pkg/cls）置顶，不影响同应用的其他分享入口
- 置顶项在列表顶部独立展示

### UI 设计
- 仿夸克风格，扁平化设计
- 灰色背景 + 白色卡片，层次分明
- 圆角元素 + 边框，视觉清晰

## 截图说明

- 主页面：应用图标、权限状态、分组管理入口、使用说明、支持场景
- 分享面板：来源信息、内容摘要、搜索栏、排序选择、分组标签、应用列表

## 技术规格

| 项目 | 规格 |
|------|------|
| 包名 | com.sharerouter |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 30 (Android 11) |
| 语言 | Java |
| 权限 | QUERY_ALL_PACKAGES |

## 编译方法

### 依赖
- JDK 17+
- Android SDK Build-Tools 34.0.0
- Android Platform 30

### 步骤

```bash
# 编译资源
aapt2 compile --dir res -o compiled_res.zip
aapt2 link -o unsigned.apk -I android.jar --manifest AndroidManifest.xml --java gen compiled_res.zip

# 编译 Java
javac -source 1.8 -target 1.8 -bootclasspath android.jar -classpath android.jar \
  -d obj src/com/sharerouter/*.java gen/com/sharerouter/R.java

# 打包 DEX
d8 --lib android.jar --output dex obj/com/sharerouter/*.class

# 组装 APK
zip -j unsigned.apk dex/classes.dex
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android aligned.apk
```

## 使用方法

1. 安装 APK 后打开「分享助手」确认权限状态
2. 在任意 App 中点击「分享」
3. 在系统分享列表中选择「分享助手」
4. 在应用列表中选择目标 App 即可转发
5. 可使用搜索、排序、分组等功能快速找到目标

## 许可证

本项目采用 **CC BY-NC-SA 4.0** (知识共享署名-非商业性使用-相同方式共享 4.0 国际) 许可证。

- 允许：学习、研究、个人使用、修改源码
- 禁止：**任何形式的商业使用**
- 要求：使用时须署名，修改后须以相同许可证发布

详见 [LICENSE](LICENSE) 文件。

## 免责声明

本软件仅供学习和个人使用，作者不对软件的使用后果承担任何责任。使用本软件即表示你同意自行承担相关风险。
