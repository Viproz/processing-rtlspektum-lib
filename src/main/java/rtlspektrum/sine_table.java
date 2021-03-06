package rtlspektrum;
import org.bridj.BridJ;
import org.bridj.Pointer;
import org.bridj.StructObject;
import org.bridj.ann.Field;
import org.bridj.ann.Library;
/**
 * <i>native declaration : line 42</i><br>
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> or <a href="http://bridj.googlecode.com/">BridJ</a> .
 */
@Library("rtlpower") 
public class sine_table extends StructObject {
	static {
		BridJ.register();
	}
	/** C type : int16_t* */
	@Field(0) 
	public Pointer<Short > Sinewave() {
		return this.io.getPointerField(this, 0);
	}
	/** C type : int16_t* */
	@Field(0) 
	public sine_table Sinewave(Pointer<Short > Sinewave) {
		this.io.setPointerField(this, 0, Sinewave);
		return this;
	}
	@Field(1) 
	public int N_WAVE() {
		return this.io.getIntField(this, 1);
	}
	@Field(1) 
	public sine_table N_WAVE(int N_WAVE) {
		this.io.setIntField(this, 1, N_WAVE);
		return this;
	}
	@Field(2) 
	public int LOG2_N_WAVE() {
		return this.io.getIntField(this, 2);
	}
	@Field(2) 
	public sine_table LOG2_N_WAVE(int LOG2_N_WAVE) {
		this.io.setIntField(this, 2, LOG2_N_WAVE);
		return this;
	}
	public sine_table() {
		super();
	}
	public sine_table(Pointer pointer) {
		super(pointer);
	}
}
