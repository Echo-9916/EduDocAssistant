// 预览页面板共享类型。
/** 高亮回调：按全文绝对闭区间滚动定位并高亮（跨块由 splitAcrossBlocks 分段）。 */
export type HighlightFn = (absStart: number, absEnd: number) => void
