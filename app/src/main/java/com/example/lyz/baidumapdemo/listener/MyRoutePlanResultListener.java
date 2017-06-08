package com.example.lyz.baidumapdemo.listener;

import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.WalkingRouteResult;

/**
 * Created by LYZ on 2017/6/8 0008.
 */

public interface MyRoutePlanResultListener  {
    void onGetWalkingRouteResult(WalkingRouteResult var1);

    void onGetBikingRouteResult(BikingRouteResult var1);
}
