## This file is a general .xdc for the Basys3 rev B board
## To use it in a project:
## - uncomment the lines corresponding to used pins
## - rename the used ports (in each line, after get_ports) according to the top level signal names in the project

set_property IOSTANDARD LVCMOS33 [get_ports *]


## Clock signal
set_property PACKAGE_PIN W5 [get_ports clock]
create_clock -add -name sys_clk_pin -period 10.00 -waveform {0 5} [get_ports clock]
 
## Switches
set_property PACKAGE_PIN V17 [get_ports io_rts]					
#set_property PACKAGE_PIN V16 [get_ports {io_binary_bits[1]}]					
#set_property PACKAGE_PIN W16 [get_ports {io_binary_bits[2]}]					
#set_property PACKAGE_PIN W17 [get_ports {io_binary_bits[3]}]					
#set_property PACKAGE_PIN W15 [get_ports {io_binary_bits[4]}]					
#set_property PACKAGE_PIN V15 [get_ports {io_binary_bits[5]}]				
#set_property PACKAGE_PIN W14 [get_ports {io_binary_bits[6]}]					
#set_property PACKAGE_PIN W13 [get_ports {io_binary_bits[7]}]					
#set_property PACKAGE_PIN V2  [get_ports {io_binary_bits[8]}]					
#set_property PACKAGE_PIN T3  [get_ports {io_binary_bits[9]}]					
#set_property PACKAGE_PIN T2  [get_ports {io_binary_bits[10]}]					
#set_property PACKAGE_PIN R3  [get_ports {io_binary_bits[11]}]					
#set_property PACKAGE_PIN W2  [get_ports {io_binary_bits[12]}]		
#set_property PACKAGE_PIN U1  [get_ports {io_binary_bits[13]}]					
#set_property PACKAGE_PIN T1  [get_ports {io_binary_bits[14]}]				
#set_property PACKAGE_PIN R2  [get_ports {io_binary_bits[15]}]					

	
##Buttons
#set_property PACKAGE_PIN U18 [get_ports {interconnectPort_wrData[13]}]						
#set_property PACKAGE_PIN T18 [get_ports {interconnectPort_wrData[14]}]			
#set_property PACKAGE_PIN W19 [get_ports {interconnectPort_rd}]			
# btnR proposed as reset
#set_property PACKAGE_PIN T17 [get_ports io_bcd_ready]						
set_property PACKAGE_PIN T17 [get_ports reset]						
#set_property PACKAGE_PIN U17 [get_ports io_binary_valid]						


## LEDs
set_property PACKAGE_PIN U16 [get_ports io_cts]					
#set_property PACKAGE_PIN E19 [get_ports {io_bcd_bits[2]}]					
#set_property PACKAGE_PIN U19 [get_ports {io_bcd_bits[3]}]					
#set_property PACKAGE_PIN V19 [get_ports {io_bcd_bits[4]}]					
#set_property PACKAGE_PIN W18 [get_ports {io_bcd_bits[5]}]					
#set_property PACKAGE_PIN U15 [get_ports {io_bcd_bits[6]}]					
#set_property PACKAGE_PIN U14 [get_ports {io_bcd_bits[7]}]					
#set_property PACKAGE_PIN V14 [get_ports {io_bcd_bits[8]}]					
#set_property PACKAGE_PIN V13 [get_ports {io_bcd_bits[9]}]					
#set_property PACKAGE_PIN V3  [get_ports {io_bcd_bits[10]}]					
#set_property PACKAGE_PIN W3  [get_ports {io_bcd_bits[11]}]					
#set_property PACKAGE_PIN U3  [get_ports {io_bcd_bits[12]}]					
#set_property PACKAGE_PIN P3  [get_ports {io_bcd_bits[13]}]					
#set_property PACKAGE_PIN N3  [get_ports {io_bcd_bits[14]}]					
#set_property PACKAGE_PIN P1  [get_ports {io_bcd_bits[15]}]					
#set_property PACKAGE_PIN L1  [get_ports {io_bcd_bits[16]}]					
	
##7 segment display
#set_property PACKAGE_PIN W7 [get_ports {io_bcd_bits[17]}]					
#set_property PACKAGE_PIN W6 [get_ports {io_bcd_bits[18]}]					
#set_property PACKAGE_PIN U8 [get_ports {io_bcd_bits[19]}]					
#set_property PACKAGE_PIN V8 [get_ports {io_bcd_bits[20]}]					
#set_property PACKAGE_PIN U5 [get_ports {io_binary_ready}]					
#set_property PACKAGE_PIN V5 [get_ports {io_bcd_valid}]					
#set_property PACKAGE_PIN U7 [get_ports {fpga_seg[6]}]					
#set_property PACKAGE_PIN V7 [get_ports {interconnectPort_rdData[15]}]							
#set_property PACKAGE_PIN U2 [get_ports {fpga_an[0]}]					
#set_property PACKAGE_PIN U4 [get_ports {fpga_an[1]}]					
#set_property PACKAGE_PIN V4 [get_ports {fpga_an[2]}]					
#set_property PACKAGE_PIN W4 [get_ports {fpga_an[3]}]					


##VGA Connector
#set_property PACKAGE_PIN G19 [get_ports {vgaRed[0]}]				
#set_property PACKAGE_PIN H19 [get_ports {vgaRed[1]}]				
#set_property PACKAGE_PIN J19 [get_ports {vgaRed[2]}]				
#set_property PACKAGE_PIN N19 [get_ports {vgaRed[3]}]				
#set_property PACKAGE_PIN N18 [get_ports {vgaBlue[0]}]				
#set_property PACKAGE_PIN L18 [get_ports {vgaBlue[1]}]				
#set_property PACKAGE_PIN K18 [get_ports {vgaBlue[2]}]				
#set_property PACKAGE_PIN J18 [get_ports {vgaBlue[3]}]				
#set_property PACKAGE_PIN J17 [get_ports {vgaGreen[0]}]				
#set_property PACKAGE_PIN H17 [get_ports {vgaGreen[1]}]				
#set_property PACKAGE_PIN G17 [get_ports {vgaGreen[2]}]				
#set_property PACKAGE_PIN D17 [get_ports {vgaGreen[3]}]				
#set_property PACKAGE_PIN P19 [get_ports Hsync]						
#set_property PACKAGE_PIN R19 [get_ports Vsync]						

 
##USB-RS232 Interface
set_property PACKAGE_PIN B18 [get_ports io_rx]						
set_property PACKAGE_PIN A18 [get_ports io_tx]						


##USB HID (PS/2)
#set_property PACKAGE_PIN C17 [get_ports PS2Clk]						
#set_property PULLUP true [get_ports PS2Clk]
#set_property PACKAGE_PIN B17 [get_ports PS2Data]					
#set_property PULLUP true [get_ports PS2Data]


##Pmod Header JA
#set_property PACKAGE_PIN J1 [get_ports {JA[0]}]					
#set_property PACKAGE_PIN L2 [get_ports {JA[1]}]					
#set_property PACKAGE_PIN J2 [get_ports {JA[2]}]					
#set_property PACKAGE_PIN G2 [get_ports {JA[3]}]					
#set_property PACKAGE_PIN H1 [get_ports {JA[4]}]					
#set_property PACKAGE_PIN K2 [get_ports {JA[5]}]					
#set_property PACKAGE_PIN H2 [get_ports {JA[6]}]					
#set_property PACKAGE_PIN G3 [get_ports {JA[7]}]					


##Pmod Header JB
#set_property PACKAGE_PIN A14 [get_ports {JB[0]}]					
#set_property PACKAGE_PIN A16 [get_ports {JB[1]}]					
#set_property PACKAGE_PIN B15 [get_ports {JB[2]}]					
#set_property PACKAGE_PIN B16 [get_ports {JB[3]}]					

## Pmod in lower row
# BTN0
#set_property PACKAGE_PIN A15 [get_ports {JB[4]}]
# BTN1
#set_property PACKAGE_PIN A17 [get_ports {JB[5]}]
# BTN2
#set_property PACKAGE_PIN C15 [get_ports {JB[6]}]
# BTN3 - use as manual clock
#set_property PACKAGE_PIN C16 [get_ports {JB[7]}]
 

##Pmod Header JC
#set_property PACKAGE_PIN K17 [get_ports {spiPort_chipSelect }] 					
#set_property PACKAGE_PIN M18 [get_ports {spiPort_dataOut }]					
#set_property PACKAGE_PIN N17 [get_ports {spiPort_dataIn }]					
#set_property PACKAGE_PIN P18 [get_ports {spiPort_spiClk }]					
#set_property PACKAGE_PIN L17 [get_ports {SD2 }]					
#set_property PACKAGE_PIN M19 [get_ports {SD3 }]					
#set_property PACKAGE_PIN P17 [get_ports {fpga_ramSelect }]					
#set_property PACKAGE_PIN R18 [get_ports {CS2 }]
