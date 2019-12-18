# Github Chat Plugin

This sample IntelliJ IDEA plugin features a chat that allows to share code.

Chat is enabled for Github-hosted projects only. Once such a project is open, a `Github Chat` tool window appears at the right. To share code, select it in the editor, right-click and choose `Share in Chat`. You can then type some comments, then click `Send` or hit `Ctrl+Enter`. For code navigation, `Ctrl-click` in the chat window. 

#### Notes / Known Issues
- It's a Gradle plugin written in Kotlin and depends on `Github` plugin
- Chat content is stored in a Github issue titled `[Chat]` and labeled `idea-chat`, it's created automatically if needed
- Code navigation doesn't always work -- I haven't figured out how to build proper PSI context for referencing.
