# App 与固件联调清单

本清单用于验证 Android App 已按当前 ESP32 固件协议完成适配。

## 预期行为

1. App 扫描并连接到设备后，自动向控制特征写入 3 条命令：
   - STATUS
   - START(muon)
   - START(timeline)
2. 固件开始通过数据特征发送 22 字节 BLE 通知。
3. App 将多个 22 字节通知重组为 512 字节 Muon 或 Timeline 包。
4. App 在仪表盘中能看到 raw packet 增长，并逐步出现 muon 或 timeline 样本。
5. 手动点击上传后，请求应成功命中后端 /api/mu-packets/。

## 设备侧验证

1. 连接设备后，观察 Android Logcat，过滤 `TelemetryRepository`。
2. 确认出现 3 次 `Sent firmware command` 日志。
3. 继续观察是否出现 `Assembled` 日志，说明 22 字节通知已被重组为完整包。

## App 侧验证

1. 在设备列表页完成扫描和连接。
2. 打开 Dashboard：
   - Raw Packets 数量应持续增长。
   - Muon Events 或 Timeline Events 应逐步出现。
   - Timeline 包出现后，位置、加速度、SiPM 数据应更新。
3. 点击上传按钮，确认界面提示上传成功。

## 后端侧验证

1. 确认当前连接设备已在后端注册，MAC 地址格式为 `AA:BB:CC:DD:EE:FF`。
2. 观察后端日志，确认 `/api/mu-packets/` 返回 200。
3. 检查设备 `last_seen_at` 是否更新。
4. 检查 IoTDB 中 muon 与 timeline 路径是否有新增记录。

## 故障定位

1. 连接成功但没有 `Sent firmware command`：检查 App 连接状态是否真正进入 `Connected`。
2. 有命令日志但没有 `Assembled`：检查固件是否实际发送 22 字节通知，或 BLE Characteristic UUID 是否匹配。
3. 有 `Assembled` 但 Dashboard 无事件：检查 Protocol 解析和事件过滤逻辑。
4. Dashboard 有事件但上传失败：检查 JWT、设备归属、MAC 注册状态与后端限流。

## 当前限制

1. 当前环境无法代替真实 BLE 硬件执行这份清单。
2. 真机联调仍需要你拿设备实际连接并观察 Logcat 与后端日志。