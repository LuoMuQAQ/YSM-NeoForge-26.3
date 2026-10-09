# 模型管理已知问题

本页只记录当前模型管理实现的未闭环能力。正常职责见[模型管理架构](../../architecture/model-management/README.md)。

- 进程 Catalog、converted 消费登记、增量 publication、direct 精确读取实例、remote/converted store、独立 consumer lease、30/60 unused target LRU 和 Cleaner fallback 已有自动化门禁；目录观察、大目录、生产多进程 prune、长时间资源/图片回收及更广泛 reload 组合仍需独立验收。
- Client 与 game server 共享不可变 local content，而 exact session、client/server runtime 和授权仍是独立 owner；remote session 通过验证后的 lean full/delta 建立 authority，再逐 entry 激活 Ready content，不把远端查询表驻留进进程 Catalog。
- Remote catalog、模型资产下载、preview cache 和 0.3/0.7 秒连续需求已接线；当前 `0.3.0-unstable` wire 下的真实 LAN/集成 server 模型切换、共享 target demand、页面关闭、在线 delta、授权矩阵、动画资产、pack cover 和重连组合仍缺完整机器可判定验证。
- `/ysm export` 能处理未准入缺图 direct 输入并生成完整可重开 `.mxc` 制品；cold miss 已接入真实 target/bake、256×256 offscreen draw/readback 和 worker encode。真实 Forge 视觉输出、游戏内命令交互、各阶段文件/host 故障矩阵、长期 cache 空间与最坏合法图片/target 成本仍未验；无 client renderer 时仅内嵌图/cache 命中可导出。
- 正常 owner 的显式 close 在最后一份 demand 释放后的下一 client tick 触发 Pending cancel；server 在下一 dispatch boundary 停止后续 fragment。已经提交给 transport 的 frame 不可撤回，遗漏 close 时的 Cleaner fallback 也不保证时限。
- Cleaner 不保证回收时限；低内存和退出过程仍需实机验证 render-thread 销毁与 native/buffer 兜底没有双释放。
