Spine Animation Extractor 使用說明

使用步驟





準備 Spine 資料夾：





將您的 Spine 資料夾（包含 .skel、.atlas 和對應的紋理檔案，例如 Bg.skel、Bg.atlas、Bg.png）放入與 target 資料夾同級的目錄。



重要：資料夾名稱必須為 Spine，不可修改。例如：



運行程式：





雙擊 clickThis.bat 檔案。



程式將自動處理 Spine 資料夾中的檔案，並生成 output.lua。



檢查輸出：





處理完成後，output.lua 將出現在與 Spine 資料夾同級的目錄（例如 D:\SpineHelperMaven\output.lua）。



打開 output.lua 檢查內容，確認動畫數據是否正確。

注意事項





資料夾名稱：Spine 資料夾名稱必須保持為 Spine，不可更改為其他名稱，否則程式無法正確運行。



環境要求：





確保系統已安裝 Java 8 或以上。



本程式已包含必要的本地庫（gdx.dll），無需額外配置。



錯誤排查：





如果 output.lua 未生成，檢查控制台輸出（運行 clickThis.bat 後會暫停顯示錯誤訊息）。



確認 Spine 資料夾包含有效的 .skel 和 .atlas 檔案，且 .atlas 檔案引用的紋理檔案存在。



若需進一步協助，請聯繫開發者並提供錯誤訊息。
