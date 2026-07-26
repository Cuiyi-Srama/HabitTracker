// Placeholder — 无需实际文件
// Firebase 配置：在编译前需要从 Firebase Console 下载 google-services.json
// 放到 app/ 目录下。
//
// 步骤：
// 1. 访问 https://console.firebase.google.com/
// 2. 创建项目（或使用已有项目）
// 3. 添加 Android 应用：com.sister.habits
// 4. 下载 google-services.json
// 5. 复制到 HabitTracker/app/google-services.json
//
// 如果不使用 Firebase 远程同步，可以：
// 1. 删除 app/build.gradle 中的 
//    id 'com.google.gms.google-services'
//    implementation platform('com.google.firebase:firebase-bom:32.2.0')
//    等相关依赖
// 2. 删除 RemoteSync.java 中的 Firebase 代码
// 3. 这样只用局域网同步 + QR码同步，完全离线可用
//
// 提示：MVP 阶段可以先不配 Firebase，局域网+QR码已经够用。
// 等基础功能跑通了再配远程同步。删除 Firebase 相关依赖后直接编译即可。