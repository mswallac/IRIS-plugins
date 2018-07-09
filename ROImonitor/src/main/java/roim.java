import ij.*;
import ij.plugin.PlugIn;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.*;
import ij.process.*;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import ij.gui.*;
import ij.gui.Plot;
import java.awt.*;
import java.awt.Point;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.*;

import javax.management.timer.Timer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.math3.util.FastMath;

import ij.measure.ResultsTable;
/*
 * This plugin takes regions of interests and monitors the mean intensity of each of them
 */
public class roim
        implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener, Runnable {
    private ImagePlus imp,niImg;                  //the ImagePlus that we listen to and the last one
    private ImagePlus plotImage;            //where we plot the profile
    private Thread bgThread;                //thread for plotting
    private boolean doUpdate,didCancel,canContinue;
    public double countval=1,max=0,min=0,reference,background,spot,background1,spot1,background2,spot2,normspot,
    		normspot1,normspot2;
    public int ref,current=0,sno=0,rno=0,bno=0,spotno,roino,sigma,rad,frameinterval,frame=0;
    public ArrayList<Double> f = new ArrayList<Double>();
    public ArrayList<ArrayList<Double>> data = new ArrayList<ArrayList<Double>>();
    public ResultsTable table  = new ResultsTable();
    public ResultsTable resultsTable;
    public RoiManager rm;
    public Overlay overlay = new Overlay();
    public Plot plot;
    public StopWatch timer;
    public JFrame roiguide,check;
    public JLabel refLabel,spotLabel,backgroundLabel;
    public JPanel selectPanel,buttonPanel;
    public JButton referenceButton,backgroundButton,spotButton;
    public DecimalFormat printformat = new DecimalFormat("0");
    public String dfrom;
    
    /* Initialization and plot for the first time. Later on, updates are triggered by the listeners **/
    public void run(String arg) {
    	
        imp = WindowManager.getCurrentImage();
        
        if (imp==null){
        	IJ.noImage(); // get and check for image, return if there is none
        	return;
       	}
        
        imp.setSlice(1);
        
        
    	
        //input for number of spots
    	GenericDialog gd = new GenericDialog("Region of Interest Monitor");
    	gd.addNumericField("Enter number of spots (can be left at one for automatic): ", 1, 1);
    	String[] method = {"Manual","Automatic"};
    	String[] datafrom = {"Live","Recorded video"};
    	gd.addChoice("Spot Detection", method, "Automatic");
    	gd.addChoice("Data Source", datafrom, "Recorded video");
    	gd.showDialog();
		if (gd.wasCanceled()) return;
		//get spot number and calculate number of ROIs correspondingly
    	spotno=(int) gd.getNextNumber();
    	String choice = gd.getNextChoice();
    	dfrom = gd.getNextChoice();
    	
    	roino=(3+((spotno-1)*2));
    	
    	imp.setOverlay(overlay);
    	
    	if(dfrom=="Recorded video") {
    		GenericDialog gd1 = new GenericDialog("Video settings");
    		gd1.addNumericField("Interval between frames (seconds)", 6, 1);
    		frameinterval=(int) gd1.getNextNumber();
        	gd1.showDialog();
    		if (gd1.wasCanceled()) return;
    	}
    	
    	if(choice=="Automatic") {
    		GenericDialog gd1 = new GenericDialog("Spot detection settings");
        	gd1.addNumericField("Gaussian blur radius (lower for dim spots): ", 8, 1);
        	gd1.addNumericField("Erosion/closing radius (lower for small/dim spots): ", 10, 1);
        	sigma=(int) gd1.getNextNumber();
        	rad=(int) gd1.getNextNumber();
        	gd1.showDialog();
    		if (gd1.wasCanceled()) return;
        	if(!roiDetector()) return;	
        	defineRefRegion();
    	}else {
        	if(!roiSelector()) return;
    		defineRefRegion();
    	}
    	
    	deleteSpots();
    	recountSpots();
    	roiSelectorAdd();
    	
    	roino=(3+((spotno-1)*2));
    	
    	timer = new StopWatch();
    	timer.start();
    	
    	if (current!=roino) { // make sure we have image and ROIs before continuing
            IJ.error("ROI Monitor","Please Select "+printformat.format(roino)+" ROIs");
            return;
        }
    	
    	
        
        
        //get image processor of plot
        ImageProcessor ip = getPlot();  
        
        //check if successful
        if (ip==null) {                     
            IJ.error("ROI Monitor","Data acquisition failed."); return;
        }
        
        // new plot window
        plotImage = new ImagePlus("Intensity of "+imp.getShortTitle(), ip);
        plotImage.show();
        IJ.wait(50);
        positionPlotWindow();
        
        // thread for plotting in the background
        bgThread = new Thread(this, "Intensity Monitoring");
        bgThread.setPriority(Math.max(bgThread.getPriority()-3, Thread.MIN_PRIORITY));
        bgThread.start();
        createListeners();
    }
    
	private void recountSpots() {
		// TODO Auto-generated method stub
		for(int i=0;i<spotno;i++) {
			overlay.get(i).setName("Spot "+(i+1));
			overlay.get(i+spotno).setName("Background "+(i+1));
		}
	}

	private void deleteSpots() {
		// TODO Auto-generated method stub
		GenericDialog gd = new GenericDialog("Select spots to remove from overlay");
		boolean[] rem = new boolean[spotno];
		for(int i=1;i<spotno+1;i++) {
			gd.addCheckbox("Spot "+i, false);
		}
    	gd.showDialog();
		if (gd.wasCanceled()) return;
		int removed=0;
		for(int i=0;i<spotno;i++) {
			rem[i] = gd.getNextBoolean();
		}
		for(int i=0;i<rem.length;i++) {
			if(rem[i]) {
				overlay.remove((overlay.getIndex("Spot "+(i+1))));
				overlay.remove((overlay.getIndex("Background "+(i+1))));
				removed++;
				spotno--;
			}	
		}
		sno-=removed;
		bno-=removed;
		current-=2*removed;
	}

	private boolean defineRefRegion() {
    	//GridBagConstraints constraint = new GridBagConstraints(); maybe use to make GUI neat
    	JPanel selectPanel = new JPanel();
    	selectPanel.setLayout(new GridLayout(3,2,0,0));
    	selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    	imp.getWindow().toFront();
    	IJ.setTool(Toolbar.RECTANGLE);
    	//initialize buttons used for adding regions of interest
    	referenceButton = new JButton("Add reference region");
    	referenceButton.setEnabled(true);
    	referenceButton.addActionListener(new ActionListener(){
    	public void actionPerformed(ActionEvent e){refDefine();}});
    	refLabel = new JLabel(" ("+printformat.format(rno)+"/1)");
    	selectPanel.add(referenceButton);
    	selectPanel.add(refLabel);
		JFrame roiguide1 = new JFrame("Select and add reference region:");
		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide1.dispose();
				overlay.clear();
			}
		});
		buttonPanel.add(cancelButton);
		//add OK button and behavior
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if(rno!=1){
					IJ.error("ROI Manager","Please select one reference region.");
				}else{
					canContinue=true;
					roiguide1.dispose();	
				}
			}
		});
		buttonPanel.add(okButton);

    	
		// Create and populate the JFrame
		roiguide1.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide1.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide1.setLocation(200,300);
		roiguide1.setVisible(true);
		roiguide1.setResizable(false);
		roiguide1.pack();

		// Wait for user to click either Cancel or OK button
		canContinue = false;
		didCancel = false;
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		return !didCancel; // if user pressed cancel return false
		
	}

	public boolean roiDetector() {
		ImagePlus imp2 = imp.duplicate();
    	IJ.run(imp2, "Gaussian Blur...", "sigma="+sigma);
		IJ.run(imp2, "Variance...", "radius="+rad);
		IJ.run(imp2, "Make Binary", "method=Default background=Default calculate black");
		ImageProcessor ip1 = imp2.getProcessor();
		Strel strel = Strel.Shape.DISK.fromRadius((int)rad);
		ImageProcessor ip2 = Morphology.closing(ip1,strel);
		ImageProcessor ip3 = Morphology.erosion(ip2,strel);
		ImagePlus imp3 = new ImagePlus("test",ip3);
		IJ.run(imp3, "Analyze Particles...", "size=3200-Infinity circularity=0.85-1.00 show=[Overlay Outlines] display include in_situ");
		Overlay spots = imp3.getOverlay();
		if(spots==null) {
			IJ.error("No spots detected.");
			return false;
		}
		overlay = spots.duplicate();
		imp.setOverlay(overlay);
		IJ.run("Labels...", "color=yellow font=8 show use");
		Roi[] rois = overlay.toArray();
		sno=rois.length;
		bno=sno;
		spotno=sno;
		current=bno+sno;
		for(int i=0;i<rois.length;i++) {
			Roi spot = rois[i];
			overlay.get(i).setName("Spot "+(i+1));
			Rectangle rect = spot.getBounds();
			int x,y,w,h,nh,ny;
			x = rect.x;
			y = rect.y;
			w = rect.width;
			h = rect.height;
			ny = y+h;
			nh = FastMath.round(((float)(.25)*h));
			imp.setRoi(x,ny,w,nh);
			Roi backr = imp.getRoi();
			backr.setName("Background "+(i+1));
			overlay.add(backr);
		}
		imp.show();
		imp.updateAndDraw();
    	return true;
    }


	public boolean roiSelector() {	
    	//GridBagConstraints constraint = new GridBagConstraints(); maybe use to make GUI neat
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(3,2,0,0));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);
		IJ.run("Labels...", "color=yellow font=8 show use");
		//initialize buttons used for adding regions of interest
		backgroundButton = new JButton("Add background 1");
		backgroundButton.setEnabled(true);
		backgroundButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				brDefine();
			}
		});
		backgroundLabel = new JLabel(" ("+printformat.format(bno)+"/"+printformat.format(spotno)+")");
		selectPanel.add(backgroundButton);
		selectPanel.add(backgroundLabel);
		
		spotButton = new JButton("Add spot 1");
		spotButton.setEnabled(true);
		spotButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				spotDefine();
			}
		});
		spotLabel = new JLabel(" ("+printformat.format(sno)+"/"+printformat.format(spotno)+")");
		selectPanel.add(spotButton);
		selectPanel.add(spotLabel);
	    
		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide.dispose();
				overlay.clear();
			}
		});
		buttonPanel.add(cancelButton);
		//add OK button and behavior
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if(current!=roino-1){
					IJ.error("ROI Manager","Please select " + printformat.format(spotno*2) + " regions of interest.");
				}else{
					canContinue=true;
					roiguide.dispose();	
				}
			}
		});
		buttonPanel.add(okButton);
		// Create and populate the JFrame
		roiguide = new JFrame("Add regions of interest:");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(200,400);
		roiguide.setVisible(true);
		roiguide.setResizable(false);
		roiguide.pack();

		// Wait for user to click either Cancel or OK button
		canContinue = false;
		didCancel = false;
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		return !didCancel; // if user pressed cancel return false
	}
	
	public boolean roiSelectorAdd() {
		JFrame roiguide2 = new JFrame();
    	//GridBagConstraints constraint = new GridBagConstraints(); maybe use to make GUI neat
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(2,2,0,0));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);
		IJ.run("Labels...", "color=yellow font=8 show use");
		//initialize buttons used for adding regions of interest
		spotButton = new JButton("Add spot "+(sno+1));
		spotButton.setEnabled(true);
		spotButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				spotDefineAdd();
			}
		});
		selectPanel.add(spotButton);
	    
		backgroundButton = new JButton("Add background "+(bno+1));
		backgroundButton.setEnabled(true);
		backgroundButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				brDefineAdd();
			}
		});
		selectPanel.add(backgroundButton);

		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide2.dispose();
				overlay.clear();
			}
		});
		buttonPanel.add(cancelButton);
		//add OK button and behavior
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				canContinue=true;
				roiguide2.dispose();	
			}
		});
		buttonPanel.add(okButton);
		
		// Create and populate the JFrame
		roiguide2.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide2.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide2.setLocation(200,400);
		roiguide2.setVisible(true);
		roiguide2.setResizable(false);
		roiguide2.pack();

		// Wait for user to click either Cancel or OK button
		canContinue = false;
		didCancel = false;
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		return !didCancel; // if user pressed cancel return false
	}
    
    //function checks the addition of each ROI (for the reference region)
    public void refDefine(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select the reference region."); return;
    	}else {
    		rno++;
        	current++;
        	Roi roi=imp.getRoi();
        	roi.setName("Reference");
    		overlay.add(roi);
    		refLabel.setText("("+printformat.format(rno)+"/1)");
    	}
		if(rno==1){
			referenceButton.setEnabled(false);
		}
    	imp.killRoi();
    }
    
    //function checks the addition of each ROI (for the background)
    public void brDefine(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select a background region"); return;
    	}else {
    		bno++;
        	current++;
        	Roi roi=imp.getRoi();
        	roi.setName("Background "+bno);
    		overlay.add(roi);
    		backgroundButton.setText("Add background "+printformat.format(bno+1));
    		backgroundLabel.setText("("+printformat.format(bno)+"/"+printformat.format(spotno)+")");
    	}
		if(bno==spotno){
			backgroundButton.setEnabled(false);
		}
    	imp.killRoi();
    }
    
    //function checks the addition of each ROI (for the actual spot)
    public void spotDefine(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select a spot."); return;
    	}else {
    		if((sno+1)>spotno){
    			IJ.error("ROI Monitor","Select only " + printformat.format(spotno) + " spot(s).");
    		}else{
    			sno++;
        		current++;
        		Roi roi=imp.getRoi();
            	roi.setName("Spot "+sno);
        		overlay.add(roi);
    			spotButton.setText("Add spot "+printformat.format(sno+1));
    			spotLabel.setText("("+printformat.format(sno)+"/"+printformat.format(spotno)+")");
    		}
    		if(sno==spotno){
    			spotButton.setEnabled(false);
    		}
    	}
    	imp.killRoi();
    }
    
    //function checks the addition of each ROI (for the background)
    public void brDefineAdd(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select a background region"); return;
    	}else {
    		bno++;
        	current++;
        	Roi roi=imp.getRoi();
        	roi.setName("Background "+bno);
    		overlay.add(roi);
    		backgroundButton.setText("Add background "+printformat.format(bno+1));
    	}
		if(bno>spotno) {
			spotno++;
		}
    	imp.killRoi();
    }
    
    //function checks the addition of each ROI (for the actual spot)
    public void spotDefineAdd(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select a spot."); return;
    	}else {
    		sno++;
        	current++;
        	Roi roi=imp.getRoi();
            roi.setName("Spot "+sno);
        	overlay.add(roi);
    		spotButton.setText("Add spot "+printformat.format(sno+1));
    		if(sno>spotno) {
    			spotno++;
    		}
    	}
    	imp.killRoi();
    }
	
	// these listeners are activated if the selection is changed in the corresponding ImagePlus
    public synchronized void mousePressed(MouseEvent e) { doUpdate = true; notify(); }   
    public synchronized void mouseDragged(MouseEvent e) { doUpdate = true; notify(); }
    public synchronized void mouseClicked(MouseEvent e) { doUpdate = true; notify(); }
    public synchronized void keyPressed(KeyEvent e) { doUpdate = true; notify(); }
    // unused listeners concerning actions in the corresponding ImagePlus
    public void mouseReleased(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseMoved(MouseEvent e) {}
    public void keyTyped(KeyEvent e) {}
    public void keyReleased(KeyEvent e) {}
    public void imageOpened(ImagePlus imp) {}

    // this listener is activated if the image content is changed (by imp.updateAndDraw)
    public synchronized void imageUpdated(ImagePlus imp) {
        if (imp == this.imp) { 
            doUpdate = true;
            notify();
        }
    }

    // if either the plot image or the image we are listening to is closed, exit
    public void imageClosed(ImagePlus imp) {
        if (imp == this.imp || imp == plotImage) {
            removeListeners();
            closePlotImage();                       //also terminates the background thread
        }
    }

    // the background thread for plotting.
    public void run() {
        while (true) {
            IJ.wait(40);                            //delay to make sure the roi has been updated
            ImageProcessor ip = getPlot();
            if (ip != null) plotImage.setProcessor(null, ip);
            synchronized(this) {
                if (doUpdate) {
                    doUpdate = false;               //and loop again
                } else {
                    try {wait();}                   //notify wakes up the thread
                    catch(InterruptedException e) { //interrupted tells the thread to exit
                        return;
                    }
                }
            }
        }
    }

    private synchronized void closePlotImage() {    //close the plot window and terminate the background thread
        bgThread.interrupt();
        plotImage.getWindow().close();
    }

    private void createListeners() {
        ImageWindow win = imp.getWindow();
        ImageCanvas canvas = win.getCanvas();
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addKeyListener(this);
        imp.addImageListener(this);
        plotImage.addImageListener(this);
    }

    private void removeListeners() {
        ImageWindow win = imp.getWindow();
        ImageCanvas canvas = win.getCanvas();
        canvas.removeMouseListener(this);
        canvas.removeMouseMotionListener(this);
        canvas.removeKeyListener(this);
        imp.removeImageListener(this);
        plotImage.removeImageListener(this);
    }

    /** Place the plot window to the right of the image window (positionPlotWindow() is TAKEN FROM IMAGEJ PROFILE PLOT FUNCTION) */
    void positionPlotWindow() {
        IJ.wait(500);
        //get plot and image windows
        if (plotImage==null || imp==null) return;
        ImageWindow pwin = plotImage.getWindow();
        ImageWindow iwin = imp.getWindow();
        //get window dimensions and screen size
        if (pwin==null || iwin==null) return;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension plotSize = pwin.getSize();
        Dimension imageSize = iwin.getSize();
        //orient windows
        if (plotSize.width==0 || imageSize.width==0) return;
        Point imageLoc = iwin.getLocation();
        int x = imageLoc.x+imageSize.width+10;
        if (x+plotSize.width>screen.width)
            x = screen.width-plotSize.width;
        pwin.setLocation(x, imageLoc.y);
        ImageCanvas canvas = iwin.getCanvas();
        canvas.requestFocus();
    }

    //update plot and give image processor
	ImageProcessor getPlot() {
        makedata();
        //plot setup
        String xLabel = "Time (s)";
        String yLabel = "Normalized Intensity";
        Color[] colors = {Color.blue,Color.green,Color.red,Color.cyan,Color.yellow};
    	plot = new Plot("",xLabel,yLabel);
    	//plot maximum of 5 normalized intensity readings
        for(int i=0;i<5 && i<spotno;i++){
        	plot.setLimits(0, f.get(f.size()-1), min, max);
        	plot.setColor(colors[i]);
        	plot.addPoints(f, data.get((i*3)+3), PlotWindow.LINE);
        	plot.addLabel(0.025, (0.1*i)+0.1, "Spot "+(i+1));
        }
        //return plot image for display
        return plot.getProcessor();
    }

    public synchronized void makedata(){
        for(int i=0;i<(spotno*3)+1;i++) {
        	data.add(new ArrayList<Double>());
        }
        double tval=(double) timer.getTime();
        double fval=frame;
        frame++;
        if(dfrom=="Recorded video") {
            f.add((double)frame*frameinterval);	
        }else {
        	f.add(tval/1000);
        }
        table.show("Data");
		table.incrementCounter();
        reference=takeroimean(overlay.get(overlay.getIndex("Reference")));
        table.addValue("Reference Intensity",reference);
        data.get(0).add(reference);
        for(int i=0;i<spotno;i++) {
        	//get intensity data
        	background=takeroimean(overlay.get(overlay.getIndex("Background "+(i+1))));
        	spot=takeroimean(overlay.get(overlay.getIndex("Spot "+(i+1))));
        	normspot=(spot-background)/reference;
        	//set graph min/max
            if(normspot>max)max=(normspot*1.1);
            if(normspot<min&&normspot>0)min=(normspot*.9);
            if(normspot<min&&normspot<0)min=(normspot*1.1);
            //add to data structure/table
            data.get((i*3)+1).add(background);
            data.get((i*3)+2).add(spot);
            data.get((i*3)+3).add(normspot);
    		table.addValue("Time",tval/1000);
    		if(dfrom=="Recorded video") {
    			table.addValue("Time",frame*frameinterval);	
            }else {
    			table.addValue("Time",tval/1000);	
            }
    		table.addValue("Normalized Spot-"+(i+1)+" Intensity",normspot);
    		table.addValue("Spot-"+(i+1)+" Intensity",spot);
    		table.addValue("Background-"+(i+1)+" Intensity",background);
        }
        IJ.wait(70);
    }

	public double takeroimean(Roi roi) {
		//check if we have an image
		ImagePlus imp = IJ.getImage();
		if (roi!=null && !roi.isArea()) roi = null;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor mask = roi!=null?roi.getMask():null;
		//get ROI dimensions
		Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
		double sum = 0;
		int count = 0;
		//take sum of pixel intensities in ROI
		for(int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask==null||mask.getPixel(x,y)!=0) {
					count++;
					sum += ip.getPixelValue(x+r.x, y+r.y);
				}
			}
		}
		//return average intensity
		return (sum/count);
	}
	}
	