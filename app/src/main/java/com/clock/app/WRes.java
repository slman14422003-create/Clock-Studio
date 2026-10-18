package com.clock.app;

final class WRes {
    private WRes() {
    }

    /** Rows: adaptive, 12 gradients, solid. Columns: the six corner radii of {@link WCfg#RADII}. */
    private static final int[][] BG = {
        {R.drawable.wb_adaptive_0,R.drawable.wb_adaptive_1,R.drawable.wb_adaptive_2,R.drawable.wb_adaptive_3,R.drawable.wb_adaptive_4,R.drawable.wb_adaptive_5},
        {R.drawable.wb_g1_0,R.drawable.wb_g1_1,R.drawable.wb_g1_2,R.drawable.wb_g1_3,R.drawable.wb_g1_4,R.drawable.wb_g1_5},
        {R.drawable.wb_g2_0,R.drawable.wb_g2_1,R.drawable.wb_g2_2,R.drawable.wb_g2_3,R.drawable.wb_g2_4,R.drawable.wb_g2_5},
        {R.drawable.wb_g3_0,R.drawable.wb_g3_1,R.drawable.wb_g3_2,R.drawable.wb_g3_3,R.drawable.wb_g3_4,R.drawable.wb_g3_5},
        {R.drawable.wb_g4_0,R.drawable.wb_g4_1,R.drawable.wb_g4_2,R.drawable.wb_g4_3,R.drawable.wb_g4_4,R.drawable.wb_g4_5},
        {R.drawable.wb_g5_0,R.drawable.wb_g5_1,R.drawable.wb_g5_2,R.drawable.wb_g5_3,R.drawable.wb_g5_4,R.drawable.wb_g5_5},
        {R.drawable.wb_g6_0,R.drawable.wb_g6_1,R.drawable.wb_g6_2,R.drawable.wb_g6_3,R.drawable.wb_g6_4,R.drawable.wb_g6_5},
        {R.drawable.wb_g7_0,R.drawable.wb_g7_1,R.drawable.wb_g7_2,R.drawable.wb_g7_3,R.drawable.wb_g7_4,R.drawable.wb_g7_5},
        {R.drawable.wb_g8_0,R.drawable.wb_g8_1,R.drawable.wb_g8_2,R.drawable.wb_g8_3,R.drawable.wb_g8_4,R.drawable.wb_g8_5},
        {R.drawable.wb_g9_0,R.drawable.wb_g9_1,R.drawable.wb_g9_2,R.drawable.wb_g9_3,R.drawable.wb_g9_4,R.drawable.wb_g9_5},
        {R.drawable.wb_g10_0,R.drawable.wb_g10_1,R.drawable.wb_g10_2,R.drawable.wb_g10_3,R.drawable.wb_g10_4,R.drawable.wb_g10_5},
        {R.drawable.wb_g11_0,R.drawable.wb_g11_1,R.drawable.wb_g11_2,R.drawable.wb_g11_3,R.drawable.wb_g11_4,R.drawable.wb_g11_5},
        {R.drawable.wb_g12_0,R.drawable.wb_g12_1,R.drawable.wb_g12_2,R.drawable.wb_g12_3,R.drawable.wb_g12_4,R.drawable.wb_g12_5},
        {R.drawable.wb_solid_0,R.drawable.wb_solid_1,R.drawable.wb_solid_2,R.drawable.wb_solid_3,R.drawable.wb_solid_4,R.drawable.wb_solid_5}
    };

    private static final int[] BORDER = {R.drawable.wb_bd_0,R.drawable.wb_bd_1,R.drawable.wb_bd_2,R.drawable.wb_bd_3,R.drawable.wb_bd_4,R.drawable.wb_bd_5};
    private static final int[] BORDER_ADAPTIVE = {R.drawable.wb_bd_adaptive_0,R.drawable.wb_bd_adaptive_1,R.drawable.wb_bd_adaptive_2,R.drawable.wb_bd_adaptive_3,R.drawable.wb_bd_adaptive_4,R.drawable.wb_bd_adaptive_5};

    static int bg(int bg, int r) {
        int k = bg == 0 ? 0 : bg <= WCfg.GRAD_LAST ? bg : WCfg.GRAD_LAST + 1;
        return BG[k][Math.max(0, Math.min(5, r))];
    }

    static int border(boolean adaptive, int r) {
        return (adaptive ? BORDER_ADAPTIVE : BORDER)[Math.max(0, Math.min(5, r))];
    }
}
