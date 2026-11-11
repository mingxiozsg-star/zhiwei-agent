## 中文

这次更新主要是把「知微长什么样、怎么说话」交回你手里，顺带把设置页重新排了一遍。

**角色卡：现在你可以直接给知微捏人设**

打开抽屉底部的「角色卡」，名字、头像、一句话身份、性格与说话风格、额外要求、开场白都能改。改完立刻生效，不用重启。

> 你说：你叫阿岚，说话别这么客气，直接点，必要时怼我。
> 知微照做：之后每次开口都按这条角色卡来，不再自我介绍成别的名字。

角色卡还能导出成 JSON 复制走，换手机或者分享给别人时粘贴回来即可；不满意随时一键恢复默认。

**设置页重排：十三个分类，点进去才是开关**

首页不再把所有选项摊开。现在一行一个分类——模型与 API、智能体人设、技能 Skills、工具与权限、使用统计、主题与字体、对话管理、工作区管理、联动扩展、网络与安全、通知、快捷键与手势、关于与赞助。每行是「标题 + 图标 + 一行小字简介」，点进去才是具体开关，返回键回到分类列表。

还没做的功能（多模型分流、SKILL.md 技能包、热力图、代理与 SSL、浏览器联动等）统一标了「开发中」，不装作能用。

**终端和工作区不再跳页**

聊天页顶栏放上终端和工作区两个图标，点一下直接盖在对话上面，再点一下收起。写完命令、看完文件，回到对话时输入框里的字还在。

**聊天里可以直接甩文件给知微**

输入栏的 ＋ 改成上传附件：选中的文件会复制进工作区 `uploads/`，以标签形式挂在输入框上方，模型能直接读，发完自动清空。

**其它**

- 提示词库（快捷短语）移到右上角 ⋮ →「快捷短语」，支持 `{time}` / `{model}` 变量
- 系统提示词升级到 v3，做事方式写得更细；你自己改过的提示词不会被覆盖
- 流式输出改成真正的逐行读取，长回复不再等到最后才出现

---

## English

This release hands the agent's identity back to you, and tidies up the settings page while we're at it.

**Persona card: shape who Zhiwei is**

Open **Persona card** from the drawer. Name, avatar, one-line identity, personality, extra instructions and greeting are all editable — saved the moment you type, no restart.

> You say: Your name is Lan. Drop the politeness, be blunt, push back when I'm wrong.
> Zhiwei complies: every reply follows that card, and it never introduces itself under another name.

Export the card as JSON to move it to another phone or share it; paste it back to import. One tap restores the defaults.

**Settings rebuilt: thirteen groups, switches one level down**

The main list no longer dumps every option on you. Each row is one group — Model & API, Persona, Skills, Tools & permissions, Usage, Theme & fonts, Conversations, Workspace, Integrations, Network & security, Notifications, Shortcuts & gestures, About & support. A row reads "title + icon + one line", and the actual switches live one level down, with Back returning to the group list.

Everything not built yet (multi-model routing, SKILL.md packs, heatmap, proxy & SSL, browser integration, …) is labelled **in progress** instead of being faked.

**Terminal and workspace no longer jump pages**

Two icons in the chat top bar lay the terminal and the workspace over the conversation; tap again to dismiss. Anything you typed in the input box is still there when you come back.

**Hand files straight to Zhiwei**

＋ in the input bar now uploads attachments: picked files are copied into the workspace `uploads/` and shown as chips above the input. The model reads them directly; they clear after sending.

**Also**

- The prompt library moved to ⋮ → **Quick phrases**, still supporting `{time}` / `{model}`
- System prompt upgraded to v3 with a more detailed way of working; prompts you edited yourself are never overwritten
- Streaming now reads line by line, so long replies no longer appear all at once at the end

---

下载 / Download：[Releases](https://github.com/mingxiozsg-star/zhiwei-agent/releases)

官方 QQ 群 / Official QQ group：**1131325287**
