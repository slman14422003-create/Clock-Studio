package com.clock.app;

final class WRes {
    private WRes() {
    }

    private static final int[][] BG = {
        {R.drawable.wb_adaptive_0,R.drawable.wb_adaptive_1,R.drawable.wb_adaptive_2,R.drawable.wb_adaptive_3},
        {R.drawable.wb_g1_0,R.drawable.wb_g1_1,R.drawable.wb_g1_2,R.drawable.wb_g1_3},
        {R.drawable.wb_g2_0,R.drawable.wb_g2_1,R.drawable.wb_g2_2,R.drawable.wb_g2_3},
        {R.drawable.wb_g3_0,R.drawable.wb_g3_1,R.drawable.wb_g3_2,R.drawable.wb_g3_3},
        {R.drawable.wb_g4_0,R.drawable.wb_g4_1,R.drawable.wb_g4_2,R.drawable.wb_g4_3},
        {R.drawable.wb_g5_0,R.drawable.wb_g5_1,R.drawable.wb_g5_2,R.drawable.wb_g5_3},
        {R.drawable.wb_g6_0,R.drawable.wb_g6_1,R.drawable.wb_g6_2,R.drawable.wb_g6_3},
        {R.drawable.wb_g7_0,R.drawable.wb_g7_1,R.drawable.wb_g7_2,R.drawable.wb_g7_3},
        {R.drawable.wb_solid_0,R.drawable.wb_solid_1,R.drawable.wb_solid_2,R.drawable.wb_solid_3}
    };

    static int bg(int bg, int r) {
        int k = bg == 0 ? 0 : bg <= WCfg.GRAD_LAST ? bg : WCfg.GRAD_LAST + 1;
        return BG[k][Math.max(0, Math.min(3, r))];
    }
}
