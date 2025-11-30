#!/usr/bin/env tclsh
# Vivado TCL for UartedFFT
set script_dir [file dirname [info script]]
set repo_root [file normalize [file join $script_dir ../../..]]

# read design sources
read_verilog -sv [file join $repo_root verilog UartedFFT.sv]

# read constraints for UART/top
read_xdc [file join $repo_root constraints basys3_uart.xdc]

# synth
synth_design -top "UartedFFT" -part "xc7a35tcpg236-1"

# place and route
opt_design
place_design
route_design

# Create reports directory
file mkdir reports

# Generate reports
puts "Generating utilization and timing reports..."
report_utilization -file "reports/utilization_report.txt"
report_utilization -hierarchical -file "reports/utilization_hierarchical.txt"

# Timing reports
report_timing_summary -file "reports/timing_summary.txt"
report_timing -max_paths 10 -file "reports/timing_detailed.txt"

puts "Reports generated successfully in reports/ directory!"
