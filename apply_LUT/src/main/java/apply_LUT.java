/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.ArrayList;
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
    public ArrayList<Float> r = new ArrayList<Float>();
    public ArrayList<Float> h = new ArrayList<Float>();
    public boolean oobret;
	
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
        if (imp==null){
        	IJ.noImage(); // get and check for image, return if there is none
        	return;
        }
		int stacks = imp.getStackSize();
		
        OpenDialog LUT = new OpenDialog("Load a LUT File.");
        while(LUT.getPath()==null){
        	IJ.wait(200);
        }
    	String path = LUT.getPath();
    	
        TextWindow tw = new TextWindow(path,100,300);
        TextPanel tp = tw.getTextPanel();
        
        for(int i=1;i<=tp.getLineCount()-1;i++){
        	String s[] = tp.getLine(i).split("    ");
        	r.add(Float.parseFloat(s[0]));
        	h.add(Float.parseFloat(s[1]));
        }
        tw.dispose();
        
    	int lw[]=imp.getDimensions();
        float pixels[][] = null;
        for(int i=1;i<=stacks;i++){
        	imp.setSlice(i);
        	pixels=imp.getProcessor().getFloatArray();
        	for(int j=0;j<lw[0];j++)
        		for(int k=0;k<lw[1];k++){
        			pixels[j][k]=interpolateLUT(pixels,pixels[j][k],j,k);
        		}
            imp.getProcessor().setFloatArray(pixels);
        }
        imp.updateAndDraw();
	}
	public float interpolateLUT(float pixels[][],float input,int j,int k){
		for(int i=0;i<r.size()-1;i++){
			if(input>r.get(i) && input<r.get(i+1)) ind=i;
			if((i==r.size()-2)&&(r.get(i+1)<input)) oobret=true;
		}
		float x1 = r.get(ind);
		float x2 = r.get(ind+1);
		float y1 = h.get(ind);
		float y2 = h.get(ind+1);
		float result=(((y2-y1)/(x2-x1))*(input-x1))+y1;
		if(oobret){
			oobret=false;
			return 0;
		}else {
			return result;
		}
	}
	public void outOfBounds(int j,int k,float pixels[][]){
		pixels[j][k]=0;
	}
}
