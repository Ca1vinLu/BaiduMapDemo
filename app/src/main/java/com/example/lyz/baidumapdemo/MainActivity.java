package com.example.lyz.baidumapdemo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRoutePlanOption;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.Trace;
import com.baidu.trace.api.track.AddPointResponse;
import com.baidu.trace.api.track.AddPointsResponse;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.LatestPoint;
import com.baidu.trace.api.track.LatestPointResponse;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.track.QueryCacheTrackRequest;
import com.baidu.trace.api.track.QueryCacheTrackResponse;
import com.baidu.trace.api.track.SupplementMode;
import com.baidu.trace.api.track.TrackPoint;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.ProcessOption;
import com.baidu.trace.model.PushMessage;
import com.baidu.trace.model.StatusCodes;
import com.baidu.trace.model.TransportMode;
import com.example.lyz.baidumapdemo.listener.MyOrientationListener;
import com.example.lyz.baidumapdemo.overLay.WalkingRouteOverlay;
import com.example.lyz.baidumapdemo.utils.MapUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnGetRoutePlanResultListener {

    private static final String TAG = "MainActivity";
    private Context context;
    private Toast mToast;

    private final int SDK_PERMISSION_REQUEST = 127;
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private String permissionInfo;


    //--定位参数
    public LocationClient mLocationClient = null;
    public BDLocationListener myListener = new MyLocationListener();
    private boolean isFirstLocate = true;
    private MyLocationConfiguration.LocationMode mCurrentMode = MyLocationConfiguration.LocationMode.NORMAL;
    private double mCurrentLatitude;
    private double mCurrentLongitude;
    private float mXDirection;
    private float mCurrentAccuracy;

    private MyOrientationListener myOrientationListener;
    //--轨迹参数

    List<LatLng> polylines = new ArrayList<>();

    // 轨迹服务ID
    private long serviceId = 141814;
    // 设备标识
    private String entityName = "myTrace";
    private MapApplication mapApplication;
    // 初始化轨迹服务
    private Trace mTrace;
    // 初始化轨迹服务客户端
    private LBSTraceClient mTraceClient;
    private OnTraceListener mTraceListener;
    private OnTrackListener mTrackListener;
    private List<LatLng> trackPoints = new ArrayList<>();
    private HistoryTrackRequest historyTrackRequest = new HistoryTrackRequest();
    private QueryCacheTrackRequest queryCacheTrackRequest = new QueryCacheTrackRequest();


    private PlanNode stNode;
    private PlanNode enNode;
    private RoutePlanSearch mSearch;
    private WalkingRouteOverlay walkingRouteOverlay;

    public Overlay polylineOverlay;
    private long startTime = (long) (System.currentTimeMillis() / 1000);


    private PowerManager powerManager = null;

    private PowerManager.WakeLock wakeLock = null;

    private TrackWakeLockReceiver trackReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        mapApplication = (MapApplication) getApplicationContext();
        powerManager = (PowerManager) mapApplication.getSystemService(Context.POWER_SERVICE);
        context = this;
        initView();
        initLocation();
        getPermissions();
        initTrace();
        initOrientationListener();
        initRoutePlanSearch();


    }


    private void initOrientationListener() {
        myOrientationListener = new MyOrientationListener(
                getApplicationContext());
        myOrientationListener.setOnOrientationListener(new MyOrientationListener.OnOrientationListener() {
                    @Override
                    public void onOrientationChanged(float x) {
                        mXDirection = x;
                        Log.d("direction", String.valueOf(mXDirection));

                        // 设置定位数据
                        MyLocationData locationData = mBaiduMap.getLocationData();

                        if (locationData != null) {
                            MyLocationData locData = new MyLocationData.Builder().accuracy(locationData.accuracy).direction(mXDirection).latitude(locationData.latitude).longitude(locationData.longitude).build();
                            mBaiduMap.setMyLocationData(locData);
                        }
                    }

                });
    }

    private void initTrace() {
        mTrace = new Trace(serviceId, entityName);
        mTraceClient = new LBSTraceClient(getApplicationContext());
        mTraceClient.setInterval(2, 10);

        // 初始化轨迹服务监听器
        mTraceListener = new OnTraceListener() {
            @Override
            public void onBindServiceCallback(int i, String s) {
                Log.d(TAG, "pushMessage:" + s);
            }

            @Override
            public void onStartTraceCallback(int i, String s) {
                Log.d(TAG, "pushMessage:" + s);
                registerReceiver();

            }

            @Override
            public void onStopTraceCallback(int i, String s) {
                Log.d(TAG, "pushMessage:" + s);

            }

            @Override
            public void onStartGatherCallback(int i, String s) {
                Log.d(TAG, "pushMessage:" + s);

            }

            @Override
            public void onStopGatherCallback(int i, String s) {
                Log.d(TAG, "pushMessage:" + s);

            }

            @Override
            public void onPushCallback(byte b, PushMessage pushMessage) {

                Log.d(TAG, "pushMessage:" + pushMessage.getMessage());
            }
        };

        mTrackListener = new OnTrackListener() {
            @Override
            public void onQueryCacheTrackCallback(QueryCacheTrackResponse queryCacheTrackResponse) {
                super.onQueryCacheTrackCallback(queryCacheTrackResponse);
            }

            @Override
            public void onLatestPointCallback(LatestPointResponse latestPointResponse) {
                super.onLatestPointCallback(latestPointResponse);
                LatestPoint point = latestPointResponse.getLatestPoint();
                LatLng latLng = MapUtils.convertTrace2Map(point.getLocation());
            }

            @Override
            public void onAddPointCallback(AddPointResponse addPointResponse) {
                super.onAddPointCallback(addPointResponse);
                Log.d(TAG, "addPoint");
            }

            @Override
            public void onAddPointsCallback(AddPointsResponse addPointsResponse) {
                super.onAddPointsCallback(addPointsResponse);
                Log.d(TAG, "addPoints");

            }

            @Override
            public void onHistoryTrackCallback(HistoryTrackResponse response) {
                int total = response.getTotal();

                if (StatusCodes.SUCCESS != response.getStatus()) {
                    Toast.makeText(context, response.getMessage(), Toast.LENGTH_SHORT).show();
                } else if (0 == total) {
//                    Toast.makeText(context, "无数据", Toast.LENGTH_SHORT).show();
                } else {


//                    Point endPoint = response.getEndPoint();
//                    startTime = endPoint.getLocTime();
//                    mToast = Toast.makeText(context, "Trace返回" + String.valueOf(total) + "个点", Toast.LENGTH_SHORT);
//                    mToast.show();
                    List<TrackPoint> points = response.getTrackPoints();
                    if (points != null) {
                        trackPoints.clear();
                        for (TrackPoint trackPoint : points) {
//                            if (!MapUtils.isZeroPoint(trackPoint.getLocation().getLatitude(), trackPoint.getLocation().getLongitude())) {
                            trackPoints.add(MapUtils.convertTrace2Map(trackPoint.getLocation()));
//                            }
                        }
                    }
                }

                //

                Log.d(TAG, String.valueOf(trackPoints.size()));
                drawCurrentTrack(trackPoints);
            }
        };

        //查询设置
        historyTrackRequest.setProcessed(true);

        // 创建纠偏选项实例
        ProcessOption processOption = new ProcessOption();
        // 设置需要去噪
        processOption.setNeedDenoise(true);
        // 设置需要抽稀
        processOption.setNeedVacuate(true);
        // 设置需要绑路
//        processOption.setNeedMapMatch(true);
        // 设置精度过滤值(定位精度大于100米的过滤掉)
        processOption.setRadiusThreshold(100);
        // 设置交通方式为驾车
        processOption.setTransportMode(TransportMode.riding);
        // 设置纠偏选项
        historyTrackRequest.setProcessOption(processOption);
        // 设置里程填充方式为驾车
        historyTrackRequest.setSupplementMode(SupplementMode.riding);
        historyTrackRequest.setServiceId(serviceId);
        historyTrackRequest.setEntityName(entityName);

    }

    private void queryHistoryTrack() {
        historyTrackRequest.setStartTime(startTime);
        historyTrackRequest.setEndTime(System.currentTimeMillis() / 1000);
        mTraceClient.queryHistoryTrack(historyTrackRequest, mTrackListener);
//        mTraceClient.queryCacheTrack(queryCacheTrackRequest, mTrackListener);
    }

    /**
     * 实时绘制轨迹
     */
    public void drawCurrentTrack(List<LatLng> points) {

//        mBaiduMap.clear();
        if (points == null || points.size() < 2) {
            return;
        }

        if (polylineOverlay != null)
            polylineOverlay.remove();

        mBaiduMap.clear();

//        if (points.size() == 1) {
//            MarkerOptions startOptions = new MarkerOptions().position(points.get(0)).icon(BitmapDescriptorFactory.fromAssetWithDpi("Icon_start.png"))
//                    .zIndex(9).draggable(true);
//            mBaiduMap.addOverlay(startOptions);
//            animateMapStatus(points.get(0), 18.0f);
//            return;
//        }

//        LatLng startPoint;
//        LatLng endPoint;
//
//        startPoint = points.get(0);
//        endPoint = points.get(points.size() - 1);


        // 添加起点图标
        OverlayOptions startOptions = new MarkerOptions().position(points.get(0)).icon(BitmapDescriptorFactory.fromAssetWithDpi("Icon_start.png"))
                .zIndex(9).draggable(true);
        mBaiduMap.addOverlay(startOptions);


//        OverlayOptions startOptions = new MarkerOptions()
//                .position(startPoint).icon(BitmapUtil.bmStart)
//                .zIndex(9).draggable(true);
//        // 添加终点图标
//        OverlayOptions endOptions = new MarkerOptions().position(endPoint)
//                .icon(bmEnd).zIndex(9).draggable(true);

        // 添加路线（轨迹）
        OverlayOptions polylineOptions = new PolylineOptions().width(10)
                .color(Color.YELLOW).points(points);

//        mBaiduMap.addOverlay(startOptions);
//        mBaiduMap.addOverlay(endOptions);

        polylineOverlay = mBaiduMap.addOverlay(polylineOptions);

//        OverlayOptions markerOptions =
//                new MarkerOptions().flat(true).anchor(0.5f, 0.5f).icon(bmArrowPoint)
//                        .position(points.get(points.size() - 1))
//                        .rotate((float) CommonUtil.getAngle(points.get(0), points.get(1)));
//        mMoveMarker = (Marker) mBaiduMap.addOverlay(markerOptions);
//
//        animateMapStatus(points);
    }

    private void initView() {
        mMapView = (MapView) findViewById(R.id.bmapView);

        mBaiduMap = mMapView.getMap();


        //普通地图
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);

        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(18.5f);
        mBaiduMap.setMapStatus(msu);


        mBaiduMap.setBuildingsEnabled(false);
        UiSettings uiSettings = mBaiduMap.getUiSettings();
        uiSettings.setOverlookingGesturesEnabled(false);

        BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory
                .fromResource(R.drawable.direction);
        MyLocationConfiguration config = new MyLocationConfiguration(mCurrentMode, true, mCurrentMarker);
        mBaiduMap.setMyLocationConfiguration(config);

        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                enNode = PlanNode.withLocation(latLng);

                if (walkingRouteOverlay != null) {
                    walkingRouteOverlay.removeFromMap();
                }


                mSearch.walkingSearch((new WalkingRoutePlanOption())
                        .from(stNode).to(enNode));

                Log.d(TAG, "map click");
            }

            @Override
            public boolean onMapPoiClick(MapPoi mapPoi) {
                return false;
            }
        });


    }

    private void initRoutePlanSearch() {
        mSearch = RoutePlanSearch.newInstance();
        mSearch.setOnGetRoutePlanResultListener(this);
        walkingRouteOverlay = new WalkingRouteOverlay(mBaiduMap);
    }


    private void initLocation() {
        mLocationClient = new LocationClient(getApplicationContext());
        //声明LocationClient类
        mLocationClient.registerLocationListener(myListener);
        //注册监听函数

        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备

        option.setCoorType("bd09ll");
        //可选，默认gcj02，设置返回的定位结果坐标系

        int span = 10000;
        option.setScanSpan(span);
        //可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的

        option.setIsNeedAddress(false);
        //可选，设置是否需要地址信息，默认不需要

        option.setOpenGps(true);
        //可选，默认false,设置是否使用gps

        option.setLocationNotify(false);
        //可选，默认false，设置是否当GPS有效时按照1S/1次频率输出GPS结果

        option.setIsNeedLocationDescribe(false);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”

        option.setIsNeedLocationPoiList(false);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到

        option.setIgnoreKillProcess(true);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死

        option.SetIgnoreCacheException(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集

        option.setEnableSimulateGps(true);
        //可选，默认false，设置是否需要过滤GPS仿真结果，默认需要

        mLocationClient.setLocOption(option);
    }


    @Override
    public void onGetWalkingRouteResult(WalkingRouteResult walkingRouteResult) {
        if (walkingRouteResult == null || walkingRouteResult.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(MainActivity.this, "抱歉，未找到结果", Toast.LENGTH_SHORT).show();
        }
        if (walkingRouteResult.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
            // 起终点或途经点地址有岐义，通过以下接口获取建议查询信息
            // result.getSuggestAddrInfo()
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("提示");
            builder.setMessage("检索地址有歧义，请重新设置。\n可通过getSuggestAddrInfo()接口获得建议查询信息");
            builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
            return;
        }
        if (walkingRouteResult.error == SearchResult.ERRORNO.NO_ERROR) {


//            if (walkingRouteResult.getRouteLines().size() > 1) {
//                nowResultwalk = walkingRouteResult;
//                if (!hasShownDialogue) {
//                    MyTransitDlg myTransitDlg = new MyTransitDlg(RoutePlanDemo.this,
//                            result.getRouteLines(),
//                            RouteLineAdapter.Type.WALKING_ROUTE);
//                    myTransitDlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                        @Override
//                        public void onDismiss(DialogInterface dialog) {
//                            hasShownDialogue = false;
//                        }
//                    });
//                    myTransitDlg.setOnItemInDlgClickLinster(new OnItemInDlgClickListener() {
//                        public void onItemClick(int position) {
//                            route = nowResultwalk.getRouteLines().get(position);
//                            WalkingRouteOverlay walkingRouteOverlay = new MyWalkingRouteOverlay(mBaidumap);
//                            mBaidumap.setOnMarkerClickListener(walkingRouteOverlay);
//                            routeOverlay = walkingRouteOverlay;
//                            walkingRouteOverlay.setData(nowResultwalk.getRouteLines().get(position));
//                            walkingRouteOverlay.addToMap();
//                            walkingRouteOverlay.zoomToSpan();
//                        }
//
//                    });
//                    myTransitDlg.show();
//                    hasShownDialogue = true;
//                }

//        } else if (walkingRouteResult.getRouteLines().size() == 1) {
            // 直接显示
//                route = result.getRouteLines().get(0);

            mBaiduMap.setOnMarkerClickListener(walkingRouteOverlay);
//                routeOverlay = walkingRouteOverlay;

            walkingRouteOverlay.setData(walkingRouteResult.getRouteLines().get(0));
//            List<WalkingRouteLine> data = walkingRouteResult.getRouteLines();
            walkingRouteOverlay.addToMap();

//            walkingRouteOverlay.zoomToSpan();

        } else {
            Log.d("route result", "结果数<0");
            return;
        }

    }


//    private class MyWalkingRouteOverlay extends WalkingRouteOverlay {
//
//        public MyWalkingRouteOverlay(BaiduMap baiduMap) {
//            super(baiduMap);
//        }
//
//        @Override
//        public BitmapDescriptor getStartMarker() {
//        if (useDefaultIcon) {
//            return BitmapDescriptorFactory.fromResource(R.drawable.direction);
//        }
//            return null;
//        }
//
//        @Override
//        public BitmapDescriptor getTerminalMarker() {
//        if (useDefaultIcon) {
//            return BitmapDescriptorFactory.fromResource(R.drawable.direction);
//        }
//            return null;
//        }
//    }

    @Override
    public void onGetTransitRouteResult(TransitRouteResult transitRouteResult) {

    }

    @Override
    public void onGetMassTransitRouteResult(MassTransitRouteResult massTransitRouteResult) {

    }

    @Override
    public void onGetDrivingRouteResult(DrivingRouteResult drivingRouteResult) {

    }

    @Override
    public void onGetIndoorRouteResult(IndoorRouteResult indoorRouteResult) {

    }

    @Override
    public void onGetBikingRouteResult(BikingRouteResult bikingRouteResult) {

    }


    public class MyLocationListener implements BDLocationListener {

        @Override
        public void onConnectHotSpotMessage(String s, int i) {

        }

        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null || mMapView == null)
                return;

            if (location.getLongitude() == 4.9e-324 && location.getLatitude() == 4.9e-324)
                return;


            if (!isFirstLocate) {
                MyLocationData myLocationData = new MyLocationData.Builder().direction(mXDirection).accuracy(mCurrentAccuracy).latitude(mCurrentLatitude).longitude(mCurrentLongitude).build();
                mBaiduMap.setMyLocationData(myLocationData);
            }

            mCurrentLatitude = location.getLatitude();
            mCurrentLongitude = location.getLongitude();
            mCurrentAccuracy = location.getRadius();
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            stNode = PlanNode.withLocation(latLng);

            if (isFirstLocate) {
                isFirstLocate = false;
                MyLocationData myLocationData = new MyLocationData.Builder().accuracy(location.getRadius()).latitude(location.getLatitude()).longitude(location.getLongitude()).build();
                mBaiduMap.setMyLocationData(myLocationData);
//                polylines.add(latLng);
            }

            queryHistoryTrack();


//            polylines.add(polylines.size() - 1, latLng);
//            PolylineOptions polylineOptions = new PolylineOptions().points(polylines).width(10).color(Color.RED);
//            mBaiduMap.addOverlay(polylineOptions);


            centreToMyLoc();

            logLocationData(location);

        }

    }

    class AutoQueryHistoryTrack implements Runnable {

        @Override
        public void run() {
            queryHistoryTrack();

        }
    }

    private void logLocationData(BDLocation location) {
        //获取定位结果
        StringBuffer sb = new StringBuffer(256);

        sb.append("time : ");
        sb.append(location.getTime());    //获取定位时间

        sb.append("\nerror code : ");
        sb.append(location.getLocType());    //获取类型类型

        sb.append("\nlatitude : ");
        sb.append(location.getLatitude());    //获取纬度信息

        sb.append("\nlontitude : ");
        sb.append(location.getLongitude());    //获取经度信息

        sb.append("\nradius : ");
        sb.append(location.getRadius());    //获取定位精准度

        if (location.getLocType() == BDLocation.TypeGpsLocation) {

            // GPS定位结果
            sb.append("\nspeed : ");
            sb.append(location.getSpeed());    // 单位：公里每小时

            sb.append("\nsatellite : ");
            sb.append(location.getSatelliteNumber());    //获取卫星数

            sb.append("\nheight : ");
            sb.append(location.getAltitude());    //获取海拔高度信息，单位米

            sb.append("\ndirection : ");
            sb.append(location.getDirection());    //获取方向信息，单位度

            sb.append("\naddr : ");
            sb.append(location.getAddrStr());    //获取地址信息

            sb.append("\ndescribe : ");
            sb.append("gps定位成功");

        } else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {

            // 网络定位结果
            sb.append("\naddr : ");
            sb.append(location.getAddrStr());    //获取地址信息

            sb.append("\noperationers : ");
            sb.append(location.getOperators());    //获取运营商信息

            sb.append("\ndescribe : ");
            sb.append("网络定位成功");

        } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {

            // 离线定位结果
            sb.append("\ndescribe : ");
            sb.append("离线定位成功，离线定位结果也是有效的");

        } else if (location.getLocType() == BDLocation.TypeServerError) {

            sb.append("\ndescribe : ");
            sb.append("服务端网络定位失败，可以反馈IMEI号和大体定位时间到loc-bugs@baidu.com，会有人追查原因");

        } else if (location.getLocType() == BDLocation.TypeNetWorkException) {

            sb.append("\ndescribe : ");
            sb.append("网络不同导致定位失败，请检查网络是否通畅");

        } else if (location.getLocType() == BDLocation.TypeCriteriaException) {

            sb.append("\ndescribe : ");
            sb.append("无法获取有效定位依据导致定位失败，一般是由于手机的原因，处于飞行模式下一般会造成这种结果，可以试着重启手机");

        }


        Log.i("BaiduLocationApiDem", sb.toString());
    }

    private void centreToMyLoc() {
        LatLng latLng = new LatLng(mCurrentLatitude, mCurrentLongitude);
        MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(latLng);
        mBaiduMap.animateMapStatus(u);

    }

    private void setMyLocationData(double latitude, double longitude, float accuracy) {

    }

    private void registerReceiver() {
        if (mapApplication.isRegisterReceiver) {
            return;
        }

        if (null == wakeLock) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "track upload");
        }
        if (null == trackReceiver) {
            trackReceiver = new TrackWakeLockReceiver(wakeLock);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(StatusCodes.GPS_STATUS_ACTION);
        mapApplication.registerReceiver(trackReceiver, filter);
        mapApplication.isRegisterReceiver = true;

    }


    @TargetApi(23)
    private void getPermissions() {

        //后台运行权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        //定位权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//如果 API level 是大于等于 23(Android 6.0) 时
            //判断是否具有权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //判断是否需要向用户解释为什么需要申请该权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(MainActivity.this, "自Android 6.0开始需要打开位置权限", Toast.LENGTH_SHORT).show();
                }
                //请求权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        mBaiduMap.setMyLocationEnabled(true);
        if (!mLocationClient.isStarted())
            mLocationClient.start();
        myOrientationListener.start();
        mTraceClient.startTrace(mTrace, mTraceListener);
        mTraceClient.startGather(mTraceListener);
        Log.d(TAG, "onStart");


    }

    @Override
    protected void onStop() {
        super.onStop();
//        mLocationClient.stop();
//        myOrientationListener.stop();
//        mTraceClient.stopGather(mTraceListener);
//        mTraceClient.stopTrace(mTrace, mTraceListener);
        Log.d(TAG, "onStop");
        if (mToast != null)
            mToast.cancel();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mToast != null)
            mToast.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }


}
