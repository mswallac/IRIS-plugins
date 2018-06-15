
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.ddogleg.optimization.*;
import org.apache.commons.lang.ArrayUtils;

public class generate_LUT implements PlugIn {
	public ImagePlus imp;
	public boolean canContinue,didCancel;
	public int thickness,above,below,increment,temp;
	public double[] filmavgs = new double[4],refavgs = new double[4],ydata = new double[4],xdata = new double[4];
	public JFrame roiguide;
	public JLabel refLabel,filmLabel;
	public String mthd,med,film;
	public static IrisUtils iu;


	@SuppressWarnings("unchecked")
	public void run(String arg){
		//open image
        String imppath = IJ.getFilePath("Open a 4-channel image.");
        imp = IJ.openImage(imppath);
        //open mirror
        String mirpath = IJ.getFilePath("Open mirror image.");
        ImagePlus mirror = IJ.openImage(mirpath);
		ImageCalculator icalc = new ImageCalculator();
		//divide to create normalized image
		ImagePlus niImg = icalc.run("Divide create 32-bit stack", imp, mirror);
		imp.close();
		imp=niImg;
		imp.updateAndDraw();
		imp.show();
		//fix image display for normalized image
		IJ.run("Enhance Contrast", "saturated=0.5");
		
		//get reference regions and lookup table parameters
        getRef();
        if(!getParams()) return;
        
        //get reflectance normalized by bare silicon reflectance
        for(int i=0;i<4;i++){
        	ydata[i]=(filmavgs[i]/refavgs[i]);
        	IJ.log(""+ydata[i]);
        }

        //put thickness in microns and initialize IrisUtils object
        double thicknessd = (((double)(thickness))/1000);
        double[] guess = {thicknessd,1,0};
        final IrisUtils iu1 = new IrisUtils(med,film,(double)temp,thicknessd);
        iu = iu1;
        
        //intialize optimizer
        UnconstrainedLeastSquares fitter = null;
        //set optimization parameters and do optimization
        if(mthd=="Relative") {
            fitter = FactoryOptimization.leastSquaresLM(1e-3, true);
        	irisFunc2 fn = new irisFunc2(iu,temp,guess[0],ydata);
        	fitter.setFunction(fn, null);
        	fitter.initialize(guess, 1e-2, 1e-6);
        	UtilOptimize.process(fitter, 1000);
        	IJ.log(fitter.getWarning());
        }else if(mthd=="Accurate") {
            fitter = FactoryOptimization.leastSquaresLM(1e-3, true);
        	irisFunc fn = new irisFunc(iu,temp,ydata);
        	fitter.setFunction(fn, null);
        	fitter.initialize(guess, 1e-5, 1e-2);
        	UtilOptimize.process(fitter, 1000);
        	IJ.log(fitter.getWarning());
        }
        
        //setup variables for lookup table
    	double[][] ic = new double[(above+below)+1][4];
        double[] d = new double[(above+below)+1],coeff = fitter.getParameters();
        int ct=0;
        
        for(int i=0;i<coeff.length;i++) {
        	IJ.log("coeff"+i+": "+coeff[i]);
        }
        
        
        //get all potential lookup tables
        if(mthd=="Relative") {
        	for(double i=-below;i<=(above);i+=increment) {
    	    	double[] values = new double[4];
            	values=irisfxn((thickness+i)/1000,coeff[0],coeff[1],temp);
            	for(int j=0;j<4;j++) {
            		ic[ct][j]=values[j];
            	}
            	d[ct] = (thickness+i);
            	ct++;
            }
        }else if(mthd=="Accurate") {
        	for(double i=-below;i<=(above);i+=increment) {
    	    	double[] values = new double[4];
            	values=irisfxn((coeff[0]+i/1000),coeff[1],coeff[2],temp);
            	for(int j=0;j<4;j++) {
            		ic[ct][j]=values[j];
            	}
            	d[ct] = (coeff[0]+i/1000)*1000;
            	ct++;
            }
        }
        
        //choose the one with the best color (most responsive/highest diffsum)
		RealMatrix mat = new Array2DRowRealMatrix(ic);
		int bestc = bestColor(ic);
		
		//make the LUT
	    makeLUT(mat.getColumn(bestc),d,bestc);
	    
	    imp.close();
	}	


	public boolean getParams() {
		GenericDialog gd = new GenericDialog("Generate LUT: ");
		gd.addNumericField("Approx. T (nm):", 100, 1);
		gd.addNumericField("Look above:", 10, 1);
		gd.addNumericField("Look below:", 10, 1);
		gd.addNumericField("Increment (nm):", 1, 1);
		gd.addNumericField("Temperature (C):", 25, 1);
		String[] methods = {"Accurate","Relative"};
		gd.addChoice("Method: ", methods ,"Accurate");
		String[] media = {"Water","Air"};
		gd.addChoice("Media: ", media ,"Water");
		String[] films = {"SiO2","PMMA"};
		gd.addChoice("Film Materal: ", films ,"SiO2");
		
		gd.showDialog();
		if (gd.wasCanceled()) {
			IJ.error("GenerateLUT cancelled.");
			return false;
		}
		thickness=(int)gd.getNextNumber();
		above=(int)gd.getNextNumber();
		below=(int)gd.getNextNumber();
		increment=(int)gd.getNextNumber();
		temp=(int)gd.getNextNumber();
		mthd = gd.getNextChoice();
		med = gd.getNextChoice();
		film = gd.getNextChoice();
		
		return true;
	}
	
	public void getRef(){
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(2,1,10,10));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);

		JButton referenceButton = new JButton("Get reference intensities");
		referenceButton.setEnabled(true);
		referenceButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					refavgs[i-1]=takeroimean(imp.getRoi());
				}
				imp.setC(1);
			}
		});
		selectPanel.add(referenceButton);

		JButton filmButton = new JButton("Get film intensities");
		filmButton.setEnabled(true);
		filmButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					filmavgs[i-1]=takeroimean(imp.getRoi());
				}
				imp.setC(1);
			}
		});
		selectPanel.add(filmButton);

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
			}
		});
		buttonPanel.add(cancelButton);

		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				canContinue=true;
				roiguide.dispose();	
			}
		});
		buttonPanel.add(okButton);

		// Create and populate the JFrame
		roiguide = new JFrame("");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(350,410);
		roiguide.setVisible(true);
		roiguide.setResizable(false);
		roiguide.pack();

		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	public double takeroimean(Roi roi) {
		ImagePlus imp = IJ.getImage();
		if (roi!=null && !roi.isArea()) roi = null;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor mask = roi!=null?roi.getMask():null;
		Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
		double sum = 0;
		int count = 0;
		for(int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask==null||mask.getPixel(x,y)!=0) {
					count++;
					sum += ip.getPixelValue(x+r.x, y+r.y);
				}
			}
		}
		return (sum/count);
	}

	
	public double[] irisfxn(double start,double m,double b,double temp){
		double sirefract,sirefract2,rsivalue,rvalue,s,filmr,medr;
		double[] result = {0,0,0,0}, im = new double[477], mir = new double[477];
		
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.3500001;i<.827;i+=0.001){
				//IJ.log("C"+j+" - "+ct);
				sirefract=iu.interpolateSI(i);
				sirefract2=iu.SiRI(i,temp);
				filmr=iu.getFilm(i,temp);
				medr=iu.getMedium(i,temp);
				rsivalue=-(iu.fresnel(1,1,sirefract,start,i));
				rvalue=(iu.fresnel(medr,filmr,sirefract2,start,i));
				s=(iu.interpolateLED(j,i));
				s=(FastMath.sqrt(s));
				//IJ.log(" nSi_l: "+sirefract+" R: "+rvalue+" RSi: "+rsivalue+" S: "+s+" MediumR: "+medr+" FilmR: "+filmr);
				if(s>=0) {
					mir[ct]=FastMath.pow(((s)*rsivalue),2);
					im[ct]=FastMath.pow(((s)*rvalue),2);
				}else {
					mir[ct]=0;
					im[ct]=0;
				}
				ct++;
			}
			result[j] = ((StatUtils.sum(im))/(StatUtils.sum(mir)));
			result[j] = ((result[j]*m) + b);
		}
		return (result);
		
	}
	
	
	
	public int bestColor(double[][] input) {
		RealMatrix mat = new Array2DRowRealMatrix(input);
		double[] diffsums = new double[4];
		int maxind;
		double max;
		for(int i=0;i<4;i++) {
			diffsums[i]=diffsum(mat.getColumn(i));
		}
		max = StatUtils.max(diffsums);
		maxind = ArrayUtils.indexOf(diffsums, max);
		return maxind;
	}
	
	public double diffsum(double[] in) {
		double sum=0;
		for(int i=0;i<in.length-1;i++) {
			sum+=(in[i+1]-in[i]);
		}
		return sum;
	}
	
	public void makeLUT(double[] bestColor, double[] d,int bestc) {
		TextWindow lut = new TextWindow("LUT-Channel_"+(bestc+1),"",400,800);
		for(int i=0;i<bestColor.length;i++)
			lut.getTextPanel().appendLine(d[i]+"    "+bestColor[i]);
	}

}


