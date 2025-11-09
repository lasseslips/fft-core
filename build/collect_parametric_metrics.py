import argparse
import subprocess
import sys
import re
import csv
import time
import json
from pathlib import Path
import shutil

# collect_parametric_metrics.py
# Run Chisel compilation with different parameters via WSL, then Vivado synthesis
# Usage (from the build folder): python collect_parametric_metrics.py [--runs N] [--config config.json] [--output results.csv]

def parse_timing_report(timing_file):
    """Parse timing summary report to extract WNS and calculate max frequency."""
    if not timing_file.exists():
        return None, None
    
    try:
        with open(timing_file, 'r') as f:
            content = f.read()
        
        # Look for "Worst Slack" in the setup timing section
        slack_match = re.search(r'Setup\s*:.*?Worst Slack\s*([\d\.]+)ns', content)
        if slack_match:
            worst_slack = float(slack_match.group(1))
            # Since this is worst slack (positive = timing met), convert to WNS format
            # WNS is typically negative when timing is violated, positive when met
            wns = worst_slack  # Keep as positive since timing is met
            
            # Look for clock period - assume 100MHz (10ns) if not found
            # For Basys3, the default clock is usually 100MHz
            target_period_ns = 10.0  # 100MHz default
            
            # Calculate maximum frequency
            # If slack is positive, we can run faster than target
            actual_period_ns = target_period_ns - worst_slack
            max_frequency_mhz = 1000 / actual_period_ns if actual_period_ns > 0 else 1000 / target_period_ns
            
            return wns, max_frequency_mhz
        
        return None, None
    except Exception as e:
        print(f"Error parsing timing report: {e}")
        return None, None

def parse_fftcore_timing(timing_detailed_file):
    """Parse detailed timing report to find the worst slack for FFTCore-internal paths."""
    if not timing_detailed_file.exists():
        return None, None
    
    try:
        with open(timing_detailed_file, 'r') as f:
            content = f.read()
        
        # Split into individual path sections
        # Each path starts with "Slack (MET)" or "Slack (VIOLATED)"
        path_sections = re.split(r'Slack \((?:MET|VIOLATED)\)', content)[1:]  # Skip header
        
        fft_paths = []
        target_period_ns = 10.0  # 100MHz default
        
        # Look through ALL paths, not just the first few
        for section in path_sections:
            # Extract slack value
            slack_match = re.search(r':\s*([\d\.-]+)ns', section)
            if not slack_match:
                continue
                
            slack = float(slack_match.group(1))
            
            # Check if this path involves FFTCore
            # A path is FFTCore-internal if both source and destination contain "fftCore"
            # or if the critical path elements are within fftCore
            
            # Look for source and destination
            source_match = re.search(r'Source:\s*([^\n]+)', section)
            dest_match = re.search(r'Destination:\s*([^\n]+)', section)
            
            if source_match and dest_match:
                source = source_match.group(1)
                dest = dest_match.group(1)
                
                # Check if this is an FFTCore-internal path
                # Path is FFTCore-internal if both endpoints are in fftCore
                source_in_fft = 'fftCore' in source
                dest_in_fft = 'fftCore' in dest
                
                # Also check for paths that cross from input registers to fftCore
                # These are still FFTCore-critical paths
                source_is_input = 'inputsRegs' in source and 'fftCore' in dest
                
                # Also check for paths from fftCore to outputs
                dest_is_output = 'fftCore' in source and ('outputMems' in dest or 'output' in dest)
                
                if (source_in_fft and dest_in_fft) or source_is_input or dest_is_output:
                    fft_paths.append({
                        'slack': slack,
                        'source': source.strip(),
                        'dest': dest.strip(),
                        'type': 'internal' if (source_in_fft and dest_in_fft) else 
                               'input_to_fft' if source_is_input else 'fft_to_output'
                    })
        
        if fft_paths:
            # Find the worst (minimum) slack among FFTCore paths
            worst_fft_slack = min(path['slack'] for path in fft_paths)
            
            # Calculate max frequency for FFTCore
            actual_period_ns = target_period_ns - worst_fft_slack
            fft_max_frequency_mhz = 1000 / actual_period_ns if actual_period_ns > 0 else 1000 / target_period_ns
            
            return worst_fft_slack, fft_max_frequency_mhz
        else:
            # No FFTCore paths found in timing report
            # This means FFTCore is not on the critical path - it's running faster than the overall design
            return None, None
        
    except Exception as e:
        print(f"Error parsing FFTCore timing: {e}")
        return None, None

def parse_utilization_report(util_file):
    """Parse utilization report to extract LUT and DSP usage."""
    if not util_file.exists():
        return None, None, None, None, None, None
    
    try:
        with open(util_file, 'r') as f:
            content = f.read()
        
        # Look for Slice LUTs pattern like: "| Slice LUTs                 | 1543 |     0 |          0 |     20800 |  7.42 |"
        lut_match = re.search(r'\|\s*Slice LUTs[^|]*\|\s*(\d+)\s*\|[^|]*\|[^|]*\|[^|]*\|\s*(\d+\.?\d*)\s*\|', content)
        
        # Look for FFs pattern like: "| Flip Flops                 | 1234 |     0 |          0 |     41600 |  2.97 |"  
        ff_match = re.search(r'\|\s*Slice Registers[^|]*\|\s*(\d+)\s*\|[^|]*\|[^|]*\|[^|]*\|\s*(\d+\.?\d*)\s*\|', content)

        # Look for DSPs pattern like: "| DSPs           |    7 |     0 |          0 |        90 |  7.78 |"
        dsp_match = re.search(r'\|\s*DSPs[^|]*\|\s*(\d+)\s*\|[^|]*\|[^|]*\|[^|]*\|\s*(\d+\.?\d*)\s*\|', content)


        
        luts_used, lut_percentage = None, None
        ffs_used, ffs_percentage = None, None
        dsps_used, dsp_percentage = None, None

        if lut_match:
            luts_used = int(lut_match.group(1))
            lut_percentage = float(lut_match.group(2))

        if ff_match:
            ffs_used = int(ff_match.group(1))
            ffs_percentage = float(ff_match.group(2))
        
        if dsp_match:
            dsps_used = int(dsp_match.group(1))
            dsp_percentage = float(dsp_match.group(2))
            
        return luts_used, lut_percentage, ffs_used, ffs_percentage, dsps_used, dsp_percentage
            
    except Exception as e:
        print(f"Error parsing utilization report: {e}")
        return None, None, None, None, None, None
    
def parse_hierarchical_utilization(hier_file, string="fftCore"):
    """Parse hierarchical utilization report to extract FFTCore-specific metrics."""
    if not hier_file.exists():
        return None, None, None, None
    
    try:
        with open(hier_file, 'r') as f:
            content = f.read()
        
        # Look for fftCore line in hierarchical report
        # Format: |   fftCore                  |     ButterflyN_30 |       7621 |       7621 |       0 |    0 | 8698 |      0 |      0 |         85 |
        fft_match = re.search(r'\|\s*' + re.escape(string) + r'\s*\|[^|]*\|\s*(\d+)\s*\|[^|]*\|[^|]*\|[^|]*\|\s*(\d+)\s*\|[^|]*\|[^|]*\|\s*(\d+)\s*\|', content)
        
        if fft_match:
            fft_luts = int(fft_match.group(1))
            fft_ffs = int(fft_match.group(2))
            fft_dsps = int(fft_match.group(3))
            
            # Calculate percentages based on total device resources 
            # Basys3 (xc7a35t): 20800 LUTs, 41600 FFs, 90 DSPs
            fft_lut_pct = (fft_luts / 20800) * 100
            fft_ffs_pct = (fft_ffs / 41600) * 100
            fft_dsp_pct = (fft_dsps / 90) * 100 if fft_dsps > 0 else 0
            
            return fft_luts, fft_lut_pct, fft_ffs, fft_ffs_pct, fft_dsps, fft_dsp_pct
        
        return None, None, None, None, None, None
            
    except Exception as e:
        print(f"Error parsing hierarchical utilization report: {e}")
        return None, None, None, None, None, None
    
def cleanup_old_results():
    """Clean up old reports and logs."""
    paths_to_clean = [
        Path("reports"),
        Path("logs"),
        Path("vivado.log"),
        Path("vivado.jou"),
        Path(".Xil")
    ]
    
    for path in paths_to_clean:
        try:
            if path.exists():
                if path.is_dir():
                    shutil.rmtree(path, ignore_errors=True)
                else:
                    path.unlink()
        except PermissionError:
            print(f"Warning: Could not remove {path} (permission denied)")
        except Exception as e:
            print(f"Warning: Could not remove {path}: {e}")

def generate_parameter_sets(config):
    """Generate different parameter sets based on configuration."""
    parameter_sets = []
    
    if "parameter_sweep" in config:
        sweep = config["parameter_sweep"]
        
        # Handle different sweep types
        if sweep["type"] == "fft_size":
            for size in sweep["values"]:
                params = config["base_parameters"].copy()
                params["fftSize"] = size
                parameter_sets.append(params)
        
        elif sweep["type"] == "data_width":
            for width in sweep["values"]:
                params = config["base_parameters"].copy()
                params["width"] = width
                parameter_sets.append(params)
        
        elif sweep["type"] == "pipeline":
            for pipeline in sweep["values"]:
                params = config["base_parameters"].copy()
                params["pipeline"] = pipeline
                parameter_sets.append(params)
        
        elif sweep["type"] == "multiple":
            # Multiple parameter sweep - generate all combinations
            import itertools
            param_names = list(sweep["parameters"].keys())
            param_values = [sweep["parameters"][name] for name in param_names]
            
            for combination in itertools.product(*param_values):
                params = config["base_parameters"].copy()
                for i, param_name in enumerate(param_names):
                    params[param_name] = combination[i]
                parameter_sets.append(params)
    
    else:
        # If no sweep specified, just use base parameters
        parameter_sets.append(config["base_parameters"])
    
    return parameter_sets

def create_scala_main(params, output_path="../src/main/scala/Main.scala.tmp"):
    """Create a temporary Main.scala file with specified parameters."""
    template = f'''import chisel3._

/**
 * An object extending App to generate the Verilog code.
 * Generated automatically with parameters: {params}
 */
object Main extends App {{
  println("I will now generate the Verilog file!")
  val fftSize = {params.get("fftSize", 8)}
  val width = {params.get("width", 16)}
  val binaryPoint = {params.get("binaryPoint", 8)}
  val pipeline = {str(params.get("pipeline", True)).lower()}
  val testCases = Seq(
      FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
  )
  emitVerilog(new FPGATestTop(fftSize, width, binaryPoint, pipeline, testCases), Array("--target-dir", "verilog"))
}}
'''
    
    Path(output_path).write_text(template)
    return output_path

def compile_chisel_design(params, run_number):
    """Compile Chisel design with given parameters via WSL."""
    print(f"  Compiling Chisel design with parameters: {params}")
    
    # Backup original Main.scala
    original_main = Path("../src/main/scala/Main.scala")
    backup_main = Path("../src/main/scala/Main.scala.backup")
    
    try:
        # Create backup
        if original_main.exists():
            shutil.copy2(original_main, backup_main)
        
        # Create new Main.scala with parameters
        temp_main = create_scala_main(params)
        shutil.move(temp_main, original_main)
        
        # Run sbt compilation via WSL (from project root)
        project_root = Path.cwd().parent  # Go up one level from build directory
        wsl_cmd = ["wsl", "bash", "-c", "cd $(wslpath -u '{}') && sbt run".format(str(project_root.as_posix()))]
        
        start_time = time.time()
        result = subprocess.run(wsl_cmd, capture_output=True, text=True)
        compile_time = time.time() - start_time
        
        if result.returncode != 0:
            print(f"  Chisel compilation failed:")
            print("  STDOUT:", result.stdout)
            print("  STDERR:", result.stderr)
            return False, compile_time
        
        print(f"  Chisel compilation completed in {compile_time:.1f}s")
        return True, compile_time
        
    finally:
        # Restore original Main.scala
        if backup_main.exists():
            shutil.move(backup_main, original_main)

def run_parametric_build(vivado_script, version, tcl_file, params, run_number):
    """Run a complete parametric build: Chisel compilation + Vivado synthesis."""
    print(f"Starting parametric run {run_number}...")
    print(f"  Parameters: {params}")
    
    # Clean up from previous run
    cleanup_old_results()
    
    # Step 1: Compile Chisel design with new parameters
    chisel_success, compile_time = compile_chisel_design(params, run_number)
    if not chisel_success:
        print(f"  Run {run_number} failed during Chisel compilation")
        return None
    
    # Step 2: Run Vivado synthesis
    cmd = [sys.executable, str(vivado_script)]
    if version:
        cmd.extend(["--version", version])
    if tcl_file:
        cmd.extend(["--tcl", str(tcl_file)])
    
    print(f"  Running Vivado synthesis...")
    start_time = time.time()
    result = subprocess.run(cmd, capture_output=True, text=True)
    synthesis_time = time.time() - start_time
    
    if result.returncode != 0:
        print(f"  Run {run_number} failed during Vivado synthesis")
        print("  STDOUT:", result.stdout)
        print("  STDERR:", result.stderr)
        return None
    
    # Step 3: Parse results
    timing_file = Path("reports/timing_summary.txt")
    timing_detailed_file = Path("reports/timing_detailed.txt")
    util_file = Path("reports/utilization_report.txt")
    hier_file = Path("reports/utilization_hierarchical.txt")
    
    wns, max_freq = parse_timing_report(timing_file)
    fft_wns, fft_max_freq = parse_fftcore_timing(timing_detailed_file)
    luts_used, lut_percentage, ffs_used, ffs_percentage, dsps_used, dsp_percentage = parse_utilization_report(util_file)
    fft_luts, fft_lut_pct, fft_ffs, fft_ffs_pct, fft_dsps, fft_dsp_pct = parse_hierarchical_utilization(hier_file, string="fftCore")
    ifft_luts, ifft_lut_pct, ifft_ffs, ifft_ffs_pct, ifft_dsps, ifft_dsp_pct = parse_hierarchical_utilization(hier_file, string="ifftCore")
    total_time = compile_time + synthesis_time
    
    metrics = {
        'run': run_number,
        'fft_size': params.get("fftSize", "N/A"),
        'data_width': params.get("width", "N/A"),
        'binary_point': params.get("binaryPoint", "N/A"),
        'pipeline': params.get("pipeline", "N/A"),
        'wns_ns': wns,
        'max_frequency_mhz': max_freq,
        'fft_wns_ns': fft_wns,
        'fft_max_frequency_mhz': fft_max_freq,
        'luts_used': luts_used,
        'lut_percentage': lut_percentage,
        'ffs_used': ffs_used,
        'ffs_percentage': ffs_percentage,
        'dsps_used': dsps_used,
        'dsp_percentage': dsp_percentage,
        'fft_luts_used': fft_luts,
        'fft_lut_percentage': fft_lut_pct,
        'fft_ffs_used': fft_ffs,
        'fft_ffs_percentage': fft_ffs_pct,
        'fft_dsps_used': fft_dsps,
        'fft_dsp_percentage': fft_dsp_pct,
        'ifft_luts_used': ifft_luts,
        'ifft_lut_percentage': ifft_lut_pct,
        'ifft_ffs_used': ifft_ffs,
        'ifft_ffs_percentage': ifft_ffs_pct,
        'ifft_dsps_used': ifft_dsps,
        'ifft_dsp_percentage': ifft_dsp_pct,
        'compile_time_s': compile_time,
        'synthesis_time_s': synthesis_time,
        'total_time_s': total_time,
        'success': True
    }
    
    # Format output with None handling
    wns_str = f"{wns:.3f}" if wns is not None else "N/A"
    freq_str = f"{max_freq:.1f}" if max_freq is not None else "N/A"
    
    # Handle special FFTCore timing values
    if fft_wns is None:
        fft_wns_str = "N/C"  # Not Critical
        fft_freq_str = "N/C"
    else:
        fft_wns_str = f"{fft_wns:.3f}" 
        fft_freq_str = f"{fft_max_freq:.1f}"
    
    lut_str = f"{luts_used}" if luts_used is not None else "N/A"
    lut_pct_str = f"{lut_percentage:.2f}" if lut_percentage is not None else "N/A"
    ff_str = f"{ffs_used}" if ffs_used is not None else "N/A"
    ff_pct_str = f"{ffs_percentage:.2f}" if ffs_percentage is not None else "N/A"
    dsp_str = f"{dsps_used}" if dsps_used is not None else "N/A"
    dsp_pct_str = f"{dsp_percentage:.2f}" if dsp_percentage is not None else "N/A"
    
    fft_lut_str = f"{fft_luts}" if fft_luts is not None else "N/A"
    fft_lut_pct_str = f"{fft_lut_pct:.2f}" if fft_lut_pct is not None else "N/A"
    fft_ff_str = f"{fft_ffs}" if fft_ffs is not None else "N/A"
    fft_ff_pct_str = f"{fft_ffs_pct:.2f}" if fft_ffs_pct is not None else "N/A"
    fft_dsp_str = f"{fft_dsps}" if fft_dsps is not None else "N/A"
    fft_dsp_pct_str = f"{fft_dsp_pct:.2f}" if fft_dsp_pct is not None else "N/A"

    ifft_lut_str = f"{ifft_luts}" if ifft_luts is not None else "N/A"
    ifft_lut_pct_str = f"{ifft_lut_pct:.2f}" if ifft_lut_pct is not None else "N/A"
    ifft_ff_str = f"{ifft_ffs}" if ifft_ffs is not None else "N/A"
    ifft_ff_pct_str = f"{ifft_ffs_pct:.2f}" if ifft_ffs_pct is not None else "N/A"
    ifft_dsp_str = f"{ifft_dsps}" if ifft_dsps is not None else "N/A"
    ifft_dsp_pct_str = f"{ifft_dsp_pct:.2f}" if ifft_dsp_pct is not None else "N/A"
    
    print(f"  Run {run_number} completed - WNS: {wns_str}ns, Max Freq: {freq_str}MHz")
    print(f"    FFTCore Timing: WNS: {fft_wns_str}ns, Max Freq: {fft_freq_str}MHz")
    print(f"    Total Resources: LUTs: {lut_str} ({lut_pct_str}%),  FFs: {ff_str} ({ff_pct_str}%),  DSPs: {dsp_str} ({dsp_pct_str}%)")
    print(f"    FFTCore Resources: LUTs: {fft_lut_str} ({fft_lut_pct_str}%), FFs: {fft_ff_str} ({fft_ff_pct_str}%), DSPs: {fft_dsp_str} ({fft_dsp_pct_str}%)")
    print(f"    IFFTCore Resources: LUTs: {ifft_lut_str} ({ifft_lut_pct_str}%), FFs: {ifft_ff_str} ({ifft_ff_pct_str}%), DSPs: {ifft_dsp_str} ({ifft_dsp_pct_str}%)")
    print(f"    Run Time: Compile: {compile_time:.1f}s, Synthesis: {synthesis_time:.1f}s")
    
    return metrics

def load_default_config():
    """Load default configuration for parameter sweeps."""
    return {
        "base_parameters": {
            "fftSize": 8,
            "width": 16,
            "binaryPoint": 8,
            "pipeline": True
        },
        "parameter_sweep": {
            "type": "fft_size",
            "values": [4, 8, 16]
        }
    }

def main():
    parser = argparse.ArgumentParser(description="Run parametric Chisel+Vivado builds with different configurations")
    parser.add_argument("--config", "-c", help="JSON configuration file (default: auto-generated)")
    parser.add_argument("--version", "-v", help="Vivado version (e.g., 2024.2)")
    parser.add_argument("--tcl", help="TCL build script (default: build.tcl)")
    parser.add_argument("--output", "-o", default="parametric_results.csv", help="Output CSV file (default: parametric_results.csv)")
    parser.add_argument("--vivado-script", default="run_vivado_script.py", help="Path to Vivado script")
    parser.add_argument("--runs", "-r", type=int, help="Override: number of runs (ignores config)")
    
    args = parser.parse_args()
    
    # Load configuration
    if args.config and Path(args.config).exists():
        with open(args.config, 'r') as f:
            config = json.load(f)
    else:
        config = load_default_config()
        if not args.config:
            # Save default config for user reference
            with open("default_config.json", 'w') as f:
                json.dump(config, f, indent=2)
            print("Created default_config.json for reference")
    
    # Generate parameter sets
    if args.runs:
        # If runs specified, repeat the base parameters
        parameter_sets = [config["base_parameters"]] * args.runs
    else:
        parameter_sets = generate_parameter_sets(config)
    
    vivado_script = Path(args.vivado_script)
    if not vivado_script.exists():
        print(f"Error: Vivado script not found: {vivado_script}")
        sys.exit(1)
    
    tcl_file = Path(args.tcl) if args.tcl else Path("build.tcl")
    if not tcl_file.exists():
        print(f"Error: TCL file not found: {tcl_file}")
        sys.exit(1)
    
    print(f"Starting {len(parameter_sets)} parametric runs...")
    print(f"Vivado script: {vivado_script}")
    print(f"TCL file: {tcl_file}")
    print(f"Output file: {args.output}")
    print("-" * 60)
    
    results = []
    successful_runs = 0
    
    for run_num, params in enumerate(parameter_sets, 1):
        metrics = run_parametric_build(vivado_script, args.version, tcl_file, params, run_num)
        if metrics:
            results.append(metrics)
            successful_runs += 1
        else:
            # Record failed run
            failed_metrics = {
                'run': run_num,
                'fft_size': params.get("fftSize", "N/A"),
                'data_width': params.get("width", "N/A"),
                'binary_point': params.get("binaryPoint", "N/A"),
                'pipeline': params.get("pipeline", "N/A"),
                'wns_ns': None,
                'max_frequency_mhz': None,
                'fft_wns_ns': None,
                'fft_max_frequency_mhz': None,
                'luts_used': None,
                'lut_percentage': None,
                'ffs_used': None,
                'ffs_percentage': None,
                'dsps_used': None,
                'dsp_percentage': None,
                'fft_luts_used': None,
                'fft_lut_percentage': None,
                'fft_ffs_used': None,
                'fft_ffs_percentage': None,
                'fft_dsps_used': None,
                'fft_dsp_percentage': None,
                'ifft_luts_used': None,
                'ifft_lut_percentage': None,
                'ifft_ffs_used': None,
                'ifft_ffs_percentage': None,
                'ifft_dsps_used': None,
                'ifft_dsp_percentage': None,
                'compile_time_s': None,
                'synthesis_time_s': None,
                'total_time_s': None,
                'success': False
            }
            results.append(failed_metrics)
        
        print("-" * 40)
    
    # Write results to CSV
    output_file = Path(args.output)
    fieldnames = [
        'run', 'fft_size', 'data_width', 'binary_point', 'pipeline',
        'wns_ns', 'max_frequency_mhz', 'fft_wns_ns', 'fft_max_frequency_mhz',
        'luts_used', 'lut_percentage', 'ffs_used', 'ffs_percentage',
        'dsps_used', 'dsp_percentage',
        'fft_luts_used', 'fft_lut_percentage', 'fft_ffs_used', 'fft_ffs_percentage',
        'fft_dsps_used', 'fft_dsp_percentage',
        'ifft_luts_used', 'ifft_lut_percentage', 'ifft_ffs_used', 'ifft_ffs_percentage',
        'ifft_dsps_used', 'ifft_dsp_percentage',
        'compile_time_s', 'synthesis_time_s', 'total_time_s', 'success'
    ]
    
    with open(output_file, 'w', newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        for result in results:
            writer.writerow(result)
    
    print(f"\nResults saved to {output_file}")
    print(f"Successful runs: {successful_runs}/{len(parameter_sets)}")

if __name__ == "__main__":
    main()