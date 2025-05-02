# Spine Animation Extractor

一個用於從Spine .skel文件中提取動畫列表並生成Lua文件的工具，無需外部依賴。

## 功能

- 遞歸掃描目錄中的所有.skel文件
- 提取並收集所有動畫名稱和持續時間
- 生成包含完整動畫信息的Lua文件，包含名稱和持續時間
- 簡單的拖放文件夾功能
- 無需Spine運行時庫
- 改進的錯誤處理，可處理不同的Spine文件格式
- 多種解析方法以最大化兼容性
- **新功能**: 調試模式，用於診斷問題
- **新功能**: 超強健的解析器，可處理極其複雜的文件格式
- **新功能**: 專門優化支持 Spine 3.8.99 版本格式
- **新功能**: 預定義動畫列表模式，可處理無法解析的文件
- **新功能**: 自動使用預定義動畫列表模式，確保所有文件都能生成動畫數據

## 快速開始 - 拖放文件夾

1. 將您的Spine文件夾拖放到`ExtractAnimations.bat`文件上
2. 該工具將：
   - 掃描文件夾及其所有子文件夾中的.skel文件
   - 從每個文件中提取動畫數據
   - 如果解析失敗，自動使用基於文件名的預定義動畫列表
   - 在文件夾中生成一個`output.lua`文件

這是不需要每次都運行命令的最簡單方法。

## 構建說明

您只需要構建項目一次。之後，您可以使用拖放功能。

### 前提條件

- Java JDK 8或更高版本（無需其他依賴）

### 使用build.bat構建

運行提供的構建腳本：
```
build.bat
```

## 使用方法

### 命令行使用

基本用法：
```
java -cp build\classes com.spinehelper.Main <spine文件夾路徑>
```

使用調試模式：
```
java -cp build\classes com.spinehelper.Main <spine文件夾路徑> --debug
```

使用預定義動畫列表模式：
```
java -cp build\classes com.spinehelper.Main <spine文件夾路徑> --force-predefined
```

結合使用多個參數：
```
java -cp build\classes com.spinehelper.Main <spine文件夾路徑> --debug --force-predefined
```

### 自動預定義動畫模式

為確保所有文件都能正確處理，本工具默認啟用了自動預定義動畫模式：

- 當正常解析方法失敗時，工具會自動使用基於文件名的預定義動畫列表
- 不同類型的文件（如背景、符號、效果等）會分配不同的預定義動畫
- 即使無法解析原始二進制格式，仍能生成有用的動畫數據
- 處理結果會顯示有多少文件使用了預定義列表
- 在調試模式下可看到完整的解析過程和回退細節

### 預定義動畫列表模式

當 Spine 文件無法通過正常方法解析時，可以使用預定義動畫列表模式：

- 此模式不嘗試解析文件內容，而是基於文件名和目錄結構分配動畫
- 適用於無法解析的特殊格式或損壞的文件
- 系統會根據文件名模式自動分配常見的動畫名稱和持續時間
- 可通過以下方式啟用：
  1. 命令行添加 `--force-predefined` 參數
  2. 在批處理腳本中選擇使用預定義動畫列表
  3. 自動模式下，在所有解析方法失敗後系統會自動使用此模式

預定義的動畫會根據文件名匹配不同的模式：
- 背景文件 (bg*.skel): 常見的背景動畫如 `MG`, `FG`, `Idle`
- 符號文件 (symbol_*.skel): 常見的符號動畫如 `Idle`, `Win`, `Frame`
- 效果文件 (fx_*.skel): 常見的特效動畫如 `Start`, `Loop`, `End`
- 等等...

### Spine 3.8.99 版本支持

本工具專門針對 Spine 3.8.99 版本進行了優化：

- 使用特定的二進制格式解析以精確提取動畫數據
- 更準確地讀取動畫持續時間
- 解析器將首先嘗試使用 Spine 3.8.99 專用解析器
- 如果專用解析器失敗，會自動回退到通用解析器

對於非 3.8.99 版本的文件，程序會自動切換到其他解析方法。

### 調試模式

當您遇到問題或想要查看詳細的處理信息時，可以啟用調試模式：

1. 命令行方式：添加`--debug`參數
2. 批處理方式：拖放文件夾到`ExtractAnimations.bat`上，然後在命令行中添加`--debug`

調試模式將提供：
- 詳細的文件搜索信息
- 目錄內容列表
- 文件解析過程的詳細日誌
- 解析方法的選擇和切換信息
- 完整的錯誤堆棧跟踪
- Spine 版本識別信息
- 解析失敗和回退到預定義列表的詳細信息

### 示例Lua輸出

該工具將生成如下所示的`output.lua`文件：

```lua
-- Spine Animation List
-- Generated on 2023-04-15 12:30:45
-- Contains data from 3 skeleton files

local SpineAnimations = {
    ["character1"] = {
        path = "characters/character1.skel",
        Animation = {
            idle = {
                name = "idle",
                isLoop = false,
                duration = 1.00
            },
            walk = {
                name = "walk",
                isLoop = false,
                duration = 0.80
            },
            run = {
                name = "run",
                isLoop = false,
                duration = 0.60
            },
            jump = {
                name = "jump",
                isLoop = false,
                duration = 0.50
            },
            attack = {
                name = "attack",
                isLoop = false,
                duration = 1.20
            }
        }
    },
    ["character2"] = {
        path = "characters/character2.skel",
        Animation = {
            idle = {
                name = "idle",
                isLoop = false,
                duration = 1.50
            },
            walk = {
                name = "walk",
                isLoop = false,
                duration = 1.00
            }
        }
    }
}

return SpineAnimations
```

## 解析方法

此工具使用多種方法從.skel文件中提取動畫數據：

1. **Spine 3.8.99 專用解析器**：針對 Spine 3.8.99 版本格式優化的解析器（推薦用於 3.8.99 版本）
2. **標準二進制解析器**：嘗試直接解析通用的二進制格式
3. **字符串模式匹配**：如果二進制解析失敗，退回到搜索文件中的動畫名稱
4. **正則表達式解析**：使用高級模式匹配尋找可能的動畫名稱
5. **跳過和尋找**：使用啟發式方法在不同的Spine版本中找到動畫
6. **超簡單二進制搜索**：掃描二進制文件中的ASCII字符串
7. **預定義動畫列表**：最後的手段，基於文件名和目錄分配常見動畫

如果一種方法失敗，工具會自動嘗試下一種方法，最大化與不同Spine版本的兼容性。如果所有解析方法都失敗，系統將自動使用預定義動畫列表作為最終解決方案。

## 項目結構

```
SpineHelper/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── spinehelper/
│                   ├── Main.java
│                   └── SpineExtractor.java
├── build.bat
├── ExtractAnimations.bat
└── README.md
```

## 注意事項

- 此工具不需要Spine運行時庫
- 直接處理.skel文件，無需atlas文件
- 文件名（不帶擴展名）用作Lua文件中的鍵
- 如果無法確定精確的持續時間，則使用默認值
- 如果遇到解析問題，系統會自動使用預定義動畫列表
- 如果需要查看詳細的處理過程，請使用`--debug`模式
- 對於 Spine 3.8.99 版本的文件有最佳支持

## 故障排除

如果您遇到任何問題：

1. 確保Java正確安裝並在您的PATH中
2. 檢查文件夾路徑不包含特殊字符
3. 在控制台輸出中查找錯誤消息
4. 嘗試使用`build.bat`重新構建項目
5. 運行時添加`--debug`參數以獲取詳細診斷信息
6. 如果所有解析方法都失敗，系統會自動使用預定義動畫列表
7. 檢查Spine文件版本，確保它們是有效的.skel文件
8. 如果某些文件不被識別，請查看它們的大小寫和擴展名是否正確
9. 確認您的Spine版本 - 如果是 3.8.99，則應該有較好的兼容性

## 如何擴展預定義動畫列表

如果預定義的動畫列表不完整或不準確，您可以編輯 `SpineExtractor.java` 文件中的 `extractFromPredefinedList` 方法，添加或修改更多的文件名模式和對應的動畫列表。

## 許可證

此項目使用的Spine運行時受Spine運行時許可協議的約束。請確保在使用此工具時遵守相關許可要求。 