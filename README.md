# DocAnalyzer 文档解析程序

## 用途说明
此程序利用火山引擎视觉理解模型将PDF文档的内容做解析并提取到Markdown文档中，以便后续将文本内容用于知识库、大模型处理等场景。

## 使用说明
1. 用Maven命令对程序打包 mvn clean package 得到 DocAnalyzer-1.0.jar
2. 开始处理文件 执行命令
`java -jar DocAnalyzer-1.0.jar batchSize model fileNameWithFullPath APIKey`
- batchSize 为必要参数且为正整数，程序会基于此数字对文档做分段调用视觉理解模型
- model参数值可以是 Model ID(具体值可参考文档 https://www.volcengine.com/docs/82379/1330310) 或者 Endpoint。此参数为必要参数
- fileNameWithFullPath 是PDF文件的全路径名称，如 /Users/bytedance/Documents/myDoc.pdf  此参数为必要参数
- APIKey 为可选参数，可以通过设置环境变量 VOLC_APIKEY 提供，如果命令行与环境变量同时设置则以命令行参数为准
3. 命令执行后可在PDF文件所在目录下找到生成的同名Markdown文件和中间过程转换出的图片文件目录

## 注意事项
1. doubao-seed-1.6 系列模型按照输入长度区间计费，为了降低成本可设置不超过 20 的 batchSize 以确保始终处于最低计价区间，并且多个分段并行解析有助于提升处理速度。但分段后有可能影响解析效果，例如跨页表格、段落等结构丢失
2. 建议简单场景采用 doubao-seed-1.6-flash 模型，复杂场景采用 doubao-seed-1.6 模型，在整体内容不超过模型总的输入输出限制情况下采用 doubao-1.5-thinking-vision-pro 或 doubao-seed-1.6 并且不做分段通常可实现最佳效果
3. 程序中已关闭深度思考。