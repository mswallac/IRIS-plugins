/*
 * apply_LUT by Michael Wallace
 * 
 * Template from IJ Process Pixels Plugin
 */

import ij.IJ;
import ij.io.*;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.plugin.*;
import ij.text.TextPanel;
import ij.text.TextWindow;

public class apply_LUT implements PlugIn {
	public ImagePlus imp;
	public ResultsTable lut;
	public int ind;
    public boolean oobret;
	
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
        if (imp==null){
        	IJ.noImage(); // get and check for image, return if there is none
        	return;
        }
		int stacks = imp.getStackSize();
		//ask for LUT path
        OpenDialog LUT = new OpenDialog("Load a LUT File.");
        while(LUT.getPath()==null){
        	IJ.wait(200);
        }
        //Load LUT into text window
    	String path = LUT.getPath();
        TextWindow tw = new TextWindow(path,100,300);
        TextPanel tp = tw.getTextPanel();
        // Read LUT values
        int lutlen = tp.getLineCount();
        float[][] lut = new float[lutlen][2];
        for(int i=1;i<=tp.getLineCount()-1;i++){
        	String s[] = tp.getLine(i).split("    ");
        	lut[i][1]=Float.parseFloat(s[0]);
        	lut[i][0]=Float.parseFloat(s[1]);
        	}
        tw.dispose(); // Close window
        
    	int lw[]=imp.getDimensions();
        float pixels[][] = null;
        //interpolate from normalized intensity to height based on the loaded lookup table for all pixels/frames
        for(int i=1;i<=stacks;i++){
        	IJ.showProgress(i, stacks);
        	IJ.showStatus("Applying LUT: "+"("+i+"/"+stacks+")");
        	imp.setSlice(i);
        	pixels=imp.getProcessor().getFloatArray();
        	for(int j=0;j<lw[0];j++)
        		for(int k=0;k<lw[1];k++){
        			pixels[j][k]=interpolate(lut,pixels[j][k],lutlen);
        		}
            imp.getProcessor().setFloatArray(pixels);
        }
        // update and draw new height map
        imp.updateAndDraw();
	}
	// basic interpolation function--returns 0 in the event there are no data point pairs that given value is between
	public float interpolate(float data[][],float input, int size){
		int ind=-1;
		for(int i=0;i<size-1;i++){
			if(input>data[i][0] && input<data[i+1][0])
				ind=i;
		}

		if(ind!=-1){
			float x1 = data[ind][0];
			float x2 = data[ind+1][0];
			float y1 = data[ind][1];
			float y2 = data[ind+1][1];
			return ((((y2-y1)/(x2-x1))*(input-x1))+y1);
		}else {
			return 0;
		}
	}
}