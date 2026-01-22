# Google Apps Script (GAS) でGoogle Tasks連携

Google Apps Scriptを使用してFastRecのGoogle Tasks連携を設定します。

## セットアップ手順

### 1. Google Apps Scriptプロジェクトの作成

1. [Google Apps Script](https://script.google.com/) にアクセス
2. 「新しいプロジェクト」をクリック
3. 画面左上のプロジェクト名をクリックして `FastRecGoogleTasks` に変更

### 2. Google Tasks APIサービスを有効化

**重要**: この手順を飛ばすとスクリプトが動作しません。

1. 左側の「サービス」アイコン（🔧）をクリック
2. 「+」ボタンをクリック
3. 「Google Tasks API」を選択
4. 「追加」をクリック

### 3. スクリプトのコードを貼り付け

1. 左側のファイル一覧で `コード.gs` をクリック
2. 以下のコードを貼り付けてください：

```javascript
// POSTリクエストを処理
function doPost(e) {
  try {
    if (!e.postData || !e.postData.contents) {
      return createResponse({ success: false, error: "No data received" });
    }

    const data = JSON.parse(e.postData.contents);
    const action = data.action || "addTask";

    if (action === "addTask") {
      return handleAddTask(data);
    } else {
      return createResponse({ success: false, error: "Unknown action: " + action });
    }
  } catch (error) {
    console.error("Error in doPost:", error);
    return createResponse({ success: false, error: error.toString() });
  }
}

// タスク追加処理
function handleAddTask(data) {
  const taskListName = data.taskListName || "fastrec";
  const title = data.title || "Untitled Task";
  const notes = data.notes || null;
  const isCompleted = data.isCompleted || false;
  const due = data.due || null;

  const taskList = getOrCreateTaskList(taskListName);
  if (!taskList) {
    return createResponse({ success: false, error: "Failed to get or create task list" });
  }

  const task = Tasks.Tasks.insert({
    title: title,
    notes: notes,
    status: isCompleted ? "completed" : "needsAction",
    due: due
  }, taskList.id);

  return createResponse({
    success: true,
    taskId: task.id
  });
}

// タスクリストを取得または作成
function getOrCreateTaskList(listName) {
  const taskLists = Tasks.Tasklists.list();
  let targetList = taskLists.items.find(list => list.title === listName);

  if (!targetList) {
    targetList = Tasks.Tasklists.insert({
      title: listName
    });
  }

  return targetList;
}

// レスポンスを作成
function createResponse(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
```

### 4. ウェブアプリとして公開

1. 「デプロイ」→「新しいデプロイ」をクリック
2. 「種類の選択」で「ウェブアプリ」を選択
3. 以下の設定を入力:
   - **説明**: `FastRec Webhook`（任意）
   - **実行ユーザー**: 「自分」
   - **アクセスできるユーザー**: **「全員」**（重要）
4. 「デプロイ」をクリック
5. **承認ダイアログが表示される場合**:
   - 「権限を確認」をクリック
   - 自分のGoogleアカウントを選択
   - 「このアプリは確認されていません」という警告が表示される場合
   - 「詳細」をクリック
   - 「（安全ではない）FastRec Webhook に移動」をクリック
   - 「許可」をクリック
6. 表示される**ウェブアプリのURL**をコピー
   - 例: `https://script.google.com/macros/s/XXXXXX/exec`

### 5. FastRecアプリの設定

1. FastRecアプリで「Google Tasks同期設定」を開く
2. 「GAS Webhook URL」欄に、コピーしたURLを貼り付け
3. 画面右上のチェックマークをタップして保存

## トラブルシューティング

### タスクが登録されない

1. **URLを確認**: 設定したURLが正しいか確認
2. **アクセス権限**: デプロイ設定で「アクセスできるユーザー」が「全員」になっているか確認
3. **サービス有効化**: 左側のリストに「Tasks」が表示されているか確認
4. **ログ確認**: Google Apps Scriptエディタで「実行」→「最近の実行」でエラーを確認

### "Tasks is not defined" エラー

Google Tasks APIサービスが有効化されていません。上記手順2を実行してください。
