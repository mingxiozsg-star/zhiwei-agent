## 中文

这次修的全是你亲口报的毛病：发了图片它说「等你授权」、不给正文只把思考过程倒出来、让它截几张图它先讲一大段计划。

**图片，现在真的能看了**

以前附件只是复制进工作区、给一个路径，模型根本没看到图，只能反过来问你「要不要我读」。现在图片会先压到长边 1568、转成多模态格式随对话发出去。

> 你说：[发一张截图] 帮我看看这里哪儿报错。
> 知微直接看图回答，不再跟你要授权。

> 你说：工作区里那个 config.json 对不对？
> 知微自己用「读文件」把内容读完再答，不用你手动贴。

**思考过程不再冒充正文**

模型吐出的思维链（reasoning / thinking）单独走一条通道，折叠在气泡里，点一下才展开。万一它只给了思考、正文一个字没有，会自动再要一次，而不是把思考全甩给你。

**废话变少了**

系统提示词升到 v4：能一句说清就不写两句，不许预告、不许复述、不许汇报过程。

> 你说：截三张图给我。
> 以前的知微：先列一条 3~5 步计划，每步再配一句进度说明……
> 现在的知微：直接调「截屏」，把图带回来，答一句结果。

**新增四个工具**

- 读文件 / 写文件 / 列目录：工作区里的文件模型能自己读写
- 截屏：首次用系统会弹一次授权，之后随时能截；截好的图直接回到对话里，模型看着图回答

**设置里多了三项**

模型与 API →「多模态与思考过程」：图片直传开关、图片压缩长边、思考过程显示开关。

**下载**

- GitHub：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## English

Every fix here came straight from what you reported: sending a picture and being told "waiting for your permission", getting a thinking chain instead of an answer, and asking for a few screenshots only to be handed a long plan first.

**Images are actually visible now**

Attachments used to be copied into the workspace and handed over as a bare path, so the model never saw the picture and asked you to authorise a read. Images are now downscaled to 1568 on the long edge and sent with the conversation as proper multimodal content.

> You: [sends a screenshot] what's failing here?
> Zhiwei: reads the picture and answers — no permission ping-pong.

> You: is `config.json` in the workspace correct?
> Zhiwei: opens it with **read file** itself and answers, no copy-pasting required.

**Reasoning no longer poses as the answer**

The model's thinking chain (reasoning / thinking) travels on its own channel and stays folded in the bubble, one tap to expand. If it returns reasoning and no answer at all, the agent asks once more for the answer instead of dumping the thinking on you.

**Far less filler**

The system prompt is now v4: if one sentence does it, don't write two — no pre-announcing, no restating, no narrating the process.

> You: take three screenshots.
> Before: a 3–5 step plan, each step with a progress note…
> Now: calls **screenshot**, brings the images back, answers with the result.

**Four new tools**

- read file / write file / list directory — the model can handle workspace files on its own
- screenshot — one system grant the first time, then instant; the capture goes straight back into the conversation so the model answers from the picture

**Three new settings**

Model & API → "Multimodal & reasoning": send images inline, image long-edge size, show reasoning.

**Download**

- GitHub: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
