package com.example.camera30;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class GraphView extends HorizontalScrollView {

    private static final String TAG = "GraphViewLibrary";
    private double graphXOffset = 1;//X position to start plotting
    private int maxAmplitude = 44000;//Maximum possible amplitude
    private double defaultWaveLength = 2.6;//default sine wave length
    private int timeMarkerSize = 50;
    private boolean drawFullGraph = false;
    private GraphSurfaceView graphSurfaceView;
    private List<WaveSample> pointList;
    private Paint paint;
    private Paint markerPaint;
    private Paint timePaint;
    private Paint needlePaint;
    private int canvasColor = Color.rgb(0, 0, 0);
    private int markerColor = Color.argb(0, 0, 0, 0);
    private int graphColor = Color.rgb(218, 165, 32);
    private int timeColor = Color.rgb(250, 250, 250);
    private int needleColor = Color.rgb(0, 0, 0);
    private boolean pausePlotting = false;
    private FrameLayout frame;
    private Context context;
    private volatile float move = 0;


    public GraphView(Context context) {
        super(context);
        init(context);
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        this.context = context;
        //FrameLayout config to hold SurfaceView
        frame = new FrameLayout(context);
        graphSurfaceView = new GraphSurfaceView(context);
        frame.addView(graphSurfaceView);
        this.setBackgroundColor(canvasColor);
        frame.setBackgroundColor(canvasColor);
        frame.setLayoutParams(new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        frame.requestLayout();
        this.addView(frame);

    }

    public void setCanvasColor(int canvasColor) {
        this.canvasColor = canvasColor;
        this.setBackgroundColor(canvasColor);
        frame.setBackgroundColor(canvasColor);
    }

    public void setGraphColor(int graphColor) {
        this.graphColor = graphColor;
        paint.setColor(graphColor);
    }

    public void showFullGraph(List<WaveSample> waveSamples) {
        graphSurfaceView.setMasterList(waveSamples);
        graphSurfaceView.showFullGraph();
    }

    public void setMasterList(List<WaveSample> list) {
        graphSurfaceView.setMasterList(list);
    }


    public void startPlotting() {
        graphSurfaceView.startPlotting();
    }

    public void setWaveLengthPX(int scale) {
        if (scale < 2) {
            scale = 2;
        }
        if (scale > 8) {
            scale = 8;
        }
        graphSurfaceView.setWaveLength(scale);
    }

    private float x1;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!drawFullGraph) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                float x2 = event.getX();
                float delta = x2 - x1;
                x1 = x2;
                move = move + delta;
                graphSurfaceView.drawFullGraph();
                break;
        }
        return super.onTouchEvent(event);
    }

    public class GraphSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
        private SurfaceHolder holder;
        private Thread _plottingThread;
        //Current rendered surface view dimensions
        private int height;
        private int halfHeight;
        private int width;

        private volatile int waveLength;
        private volatile boolean isRunning = false;
        private volatile boolean stop = false;
        private int widthForFullGraph = 50;
        int listMasterSize = 0;
        int redrawCount = 0;
        int freezCount = 0;
        int sleepTime = 5;
        private int deltaWidth;

        public GraphSurfaceView(Context context) {
            super(context);
            init(context);
        }

        public GraphSurfaceView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        public void setWaveLength(int scale) {
            waveLength = scale;
        }

        public void init(Context context) {
            //Setting up surface view with default dimensions
            this.setLayoutParams(new FrameLayout.LayoutParams(100, 100));

            //Set wave length in mm
            waveLength = (int) (context.getResources().getDisplayMetrics().xdpi / (20.4 * defaultWaveLength));
            holder = getHolder();
            holder.addCallback(this);



            //Paint config for waves
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(graphColor);
            paint.setStrokeWidth(1);
            paint.setStyle(Paint.Style.STROKE);
            //Paint config for amplitude needle
            needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            needlePaint.setColor(needleColor);
            needlePaint.setStrokeWidth(1);
            needlePaint.setStyle(Paint.Style.STROKE);
            //Paint config for time text
            timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            timePaint.setColor(timeColor);
            timePaint.setStrokeWidth(1);
            timePaint.setTextSize(timeMarkerSize / context.getResources().getDisplayMetrics().scaledDensity);
            timePaint.setStyle(Paint.Style.STROKE);
            //Paint config for right side marker
            markerPaint = new Paint();
            markerPaint.setColor(graphColor);
            markerPaint.setStyle(Paint.Style.STROKE);
        }

        /**
         * Function to calculate time to x and amplitude to y mapping of each sample for current frame
         */
        private void processAmplitude() {
            //calculate sleep time and redraw count for smooth wave movement
            if (pointList.size() != listMasterSize) {//new sample found since last frame
                listMasterSize = pointList.size();
                freezCount = -1;
                redrawCount = 0;
            } else {//same no of samples since last frame so move the wave to left by incrementing redrawCount
                redrawCount++;
                if (redrawCount > waveLength) {//Moved too much left still no new sample
                    freezCount++;
                    if (freezCount > 0) {//Increase sleep time to avoid wave redraw
                        sleepTime = sleepTime + 1;
                    } else if (freezCount < 0) {
                        sleepTime = sleepTime - 1;
                        if (sleepTime < 0) {
                            sleepTime = 0;
                        }
                    }
                    redrawCount = waveLength;
                }
            }
            //Path variable for wave
            Path graphPath = new Path();

            //HashMap to hold time markers position
            HashMap<Integer, String> timeMap = new HashMap<>();

            //Initialize start position for wave path
            int x = (int) (width * graphXOffset);
            int listSize = pointList.size() - 1;

            //Path variable for marker
            Path markerPath = new Path();
            markerPaint.setStrokeWidth((float) (width - (width * graphXOffset)));
            markerPath.moveTo(x + (width / 5), 0);
            markerPath.lineTo(x + (width / 5), height);

            //Path variable for needle
            Path needlePath = new Path();

            /*
            Draw sine waves for last 'n' no of samples.
            'n' is calculated from no x - direction pixels available in surface view from width * 3/4 to 0 - wavelength.
            Each sample will be drawn as a sine wave with waveLength as width
            */
            for (int i = listSize - 1; x >= -waveLength; x = x - waveLength) {
                if (i >= 0) {
                    if (i == 0) {

                    } else {
                    }

                    int amplitude = (int) pointList.get(i).getAmplitude();
                    drawAmplitude(amplitude, x, graphPath, needlePath);
                }
                i--;
            }
            renderAmplitude(timeMap, graphPath, markerPath, needlePath);
        }

        /**
         * Draw sine wave path for current sample at x position with amplitude and needle path to show current amplitude
         */
        private void drawAmplitude(int amplitude, int x, Path graphPath, Path needlePath) {

            /* Calculate no y pixels for sine wave magnitude from amplitude */
            amplitude = halfHeight * amplitude / maxAmplitude;
            /*  If current sample is the latest then move needle to show current amplitude    */
            if (x == (int) (width * graphXOffset)) {
                needlePath.moveTo((float) (width * graphXOffset), halfHeight - amplitude);
                needlePath.lineTo(width, halfHeight - amplitude);
            }
            if (amplitude > 0) {

                /*  Below code can be customized to support more graph types
                 *  Draw a sine wave from x-redrawCount to x - redrawCount + waveLength with positive magnitude at halfHeight - amplitude and negative at halfHeight + amplitude    */
                RectF oval = new RectF();
                oval.set(x - redrawCount, halfHeight - amplitude, x - redrawCount + (waveLength / 2)+1, halfHeight + amplitude);
                graphPath.addArc(oval, 180, 400);
                oval.set(x - redrawCount + (waveLength / 2), halfHeight - amplitude, x - redrawCount + (waveLength)+1, halfHeight + amplitude);
                graphPath.addArc(oval, 180, 400);
            } else {
                /*  Draw simple line to represent 0 */
                graphPath.moveTo(x - redrawCount, halfHeight);
                graphPath.lineTo(x - redrawCount + waveLength, halfHeight);
            }
        }

        /**
         * Draw all the path on SurfaceView canvas
         */
        private void renderAmplitude(HashMap<Integer, String> timeMap, Path tempPath, Path markerPath, Path needlePath) {
            Canvas tempCanvas = null;
            if (holder.getSurface().isValid()) {//SurfaceView available
                try {
                    tempCanvas = holder.lockCanvas();
                    synchronized (holder) {
                        if (tempCanvas != null) {
                            /*  Clean SurfaceView with plain canvas color   */
                            tempCanvas.drawColor(canvasColor);

                            //Draw time texts
                            Set<Integer> keys = timeMap.keySet();
                            for (int key : keys) {
                                tempCanvas.drawText(timeMap.get(key), key, 10, timePaint);
                            }
                            /*  Draw sine waves, marker and needle  */
                            tempCanvas.drawPath(tempPath, paint);
                            if (markerPath != null) {
                                tempCanvas.drawPath(markerPath, markerPaint);
                            }
                            if (needlePath != null) {
                                tempCanvas.drawPath(needlePath, needlePaint);
                            }
                        }
                    }
                } finally {
                    if (tempCanvas != null) {
                        holder.unlockCanvasAndPost(tempCanvas);
                    }
                }
            }
            try {
                /*  Sleep the thread to reduce CPU usage and avoid same wave redrawn    */
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "Created");
            /*  Configure width for current mode  */
            this.setLayoutParams(new LayoutParams(GraphView.this.getWidth(), GraphView.this.getHeight()));
            /*  Continue plotting on app switches between foreground and background  */
            if (isRunning && !_plottingThread.isAlive()) {
                startPlotting();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            Log.d(TAG, "Changed");
            //Redraw full graph if needed
            if (drawFullGraph) {
                drawFullGraph();
            }
            //Reset will get current rendered dimensions
            reset();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "Destroyed");
            //Stop the plotting if app goes to background
            this.stop = true;
            if (_plottingThread != null) {
                _plottingThread.interrupt();
            }
        }

        /**
         * Reset the surface view with plain canvas color and get current rendered dimensions
         */
        public void reset() {
            height = getHeight();
            halfHeight = height / (2);
            width = getWidth();
            Canvas tempCanvas = null;
            if (holder.getSurface().isValid()) {
                try {
                    tempCanvas = holder.lockCanvas();
                    synchronized (holder) {
                        if (tempCanvas != null) {
                            tempCanvas.drawColor(canvasColor);
                        }
                    }
                } finally {
                    if (tempCanvas != null) {
                        holder.unlockCanvasAndPost(tempCanvas);
                    }
                }
            }
        }

        public void setMasterList(List<WaveSample> list) {
            pointList = list;
        }

        /**
         * Calculate no of pixels needed in x direction to display all available samples in the point list and set it as surface view's width
         * Will trigger surface change after new dimensions
         */
        public void showFullGraph() {
            if (pointList == null) {
                return;
            }
            if (pointList.size() == 0) {
                return;
            }
            drawFullGraph = true;
            reset();
            this.stop = true;
            isRunning = false;
            if (_plottingThread != null) {
                _plottingThread.interrupt();
            }
            widthForFullGraph = pointList.size() * waveLength + 50;
            drawFullGraph();
        }

        private void drawFullGraph() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    deltaWidth = width - widthForFullGraph;
                    if (move > 0) {
                        move = 0;
                    }
                    if (deltaWidth < 0) {
                        if (move < deltaWidth) {
                            move = deltaWidth;
                        }
                    } else {
                        move = 0;
                    }
                    int sampleNo;
                    int x = 0;
                    if (widthForFullGraph < width) {
                        sampleNo = pointList.size();
                    } else {
                        sampleNo = (int) ((width + waveLength + Math.abs(move)) / waveLength);
                    }
                    if (sampleNo > pointList.size()) {
                        sampleNo = pointList.size();
                    }
                    HashMap<Integer, String> timeMap1 = new HashMap<>();
                    Path tempPath1 = new Path();

                    for (int i = (int) (Math.abs(move) / waveLength); i <= sampleNo - 1; i++) {
                        if (i == 0) {
                            timeMap1.put(x, " ");
                        }
                        int amplitude = (int) pointList.get(i).getAmplitude();
                        amplitude = halfHeight * amplitude / maxAmplitude;
                        if (amplitude > 0) {
                            RectF oval = new RectF();
                            oval.set(x, halfHeight - amplitude, x + (waveLength / 2), halfHeight + amplitude);
                            tempPath1.addArc(oval, 180, 400);
                            oval.set(x + (waveLength / 2), halfHeight - amplitude, x + (waveLength), halfHeight + amplitude);
                            tempPath1.addArc(oval, 0, 400);
                        } else {
                            tempPath1.moveTo(x, halfHeight);
                            tempPath1.lineTo(x + waveLength, halfHeight);
                        }
                        x = x + waveLength;
                    }
                    renderAmplitude(timeMap1, tempPath1, null, null);
                }
            }).start();
        }

        public void startPlotting() {
            drawFullGraph = false;
            reset();
            this.stop = false;
            isRunning = true;
            _plottingThread = new Thread(this);
            _plottingThread.start();
        }

        @Override
        public void run() {
            while (!this.stop) {
                if (!pausePlotting) {
                    processAmplitude();
                }
            }
        }
    }
}


