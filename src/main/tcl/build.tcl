#!/usr/bin/env tclsh
# https://projectf.io/posts/vivado-tcl-build-script/
# Resolve repository root relative to this script so relative paths work
set script_dir [file dirname [info script]]
# src/main/tcl -> go up three levels to repo root
set repo_root [file normalize [file join $script_dir ../../..]]

# read design sources (add one line for each file)
read_verilog -sv [file join $repo_root verilog FPGATestTop.sv]

# read constraints
read_xdc [file join $repo_root constraints basys3_test_harness.xdc]

# synth
synth_design -top "FPGATestTop" -part "xc7a35tcpg236-1"

# place and route
opt_design
place_design
route_design

# Create reports directory
file mkdir reports

# Generate reports
puts "Generating utilization and timing reports..."

# Utilization reports
report_utilization -file "reports/utilization_report.txt"
report_utilization -hierarchical -file "reports/utilization_hierarchical.txt"

# Timing reports (WNS gives you the maximum frequency information)
report_timing_summary -file "reports/timing_summary.txt"
report_timing -max_paths 10 -file "reports/timing_detailed.txt"

puts "Reports generated successfully in reports/ directory!"

# write bitstream
# write_bitstream -force "hello.bit"
