/* teVirtualMIDI user-space interface - v1.2.9.37
 *
 * Copyright 2009-2015, Tobias Erichsen
 * All rights reserved, unauthorized usage & distribution is prohibited.
 *
 * For technical or commercial requests contact: info <at> tobias-erichsen <dot> de
 *
 * teVirtualMIDI.sys is a kernel-mode device-driver which can be used to dynamically create & destroy
 * midiports on Windows (XP to Windows 8, 32bit & 64bit).  The "back-end" of teVirtualMIDI which is
 * defined by this include file of teVirtualMIDI can be used to create & destroy such ports and receive
 * and transmit data from/to those created ports.
 *
 * The DLL sets the last-error for the thread that called one of the functions below when
 * internal errors come up - those errors can be retrieved via GetLastError().
 * Here some of the more specific errors that you can encounter:
 *
 * ERROR_OLD_WIN_VERSION:
 *	Your Windows-version is too old for dynamic MIDI-port creation.
 *	You need at least Service-Pack 2 XP and W2K3.  Vista and Windows 7 work out of the box
 *
 * ERROR_INVALID_NAME:
 *	The name you specified contains invalid characters or is too short.
 *
 * ERROR_ALREADY_EXISTS,ERROR_ALIAS_EXISTS:
 *	The name you specified is already existing (either as a port created by virtualMIDI or
 *	by another MIDI-driver)
 *
 * ERROR_PATH_NOT_FOUND:
 *	The driver could not be opened.  Possibly the driver was not installed or is inactive.
 *
 * ERROR_REVISION_MISMATCH:
 *	Due to some installation issue the virtualMIDIxx.dll in use and the virtualMIDI.sys
 *	driver-version do not match.  Therefore operation is not possible!
 */

#ifndef _VIRTUAL_MIDI_H_
#define _VIRTUAL_MIDI_H_

#include <windows.h>

#ifdef __cplusplus
extern "C" {
#endif


/* Bits in Mask to enable logging for specific areas */
/* TE_VM_LOGGING_MISC - log internal stuff (port enable, disable...) */
#define TE_VM_LOGGING_MISC	1
/* TE_VM_LOGGING_RX - log data received from the driver */
#define TE_VM_LOGGING_RX	2
/* TE_VM_LOGGING_TX - log data sent to the driver */
#define TE_VM_LOGGING_TX	4

/* This is the size of the buffer that is being used for communication
 * with the driver when instanciating a port with the old, deprecated
 * "virtualMIDICreatePort" function.  This value is currently 128kb - 1,
 * but may change anytime in the future.  This value also limits the
 * maximum size of received sysex-data due to the parser in the merger-
 * module in the driver.
 */
#define TE_VM_DEFAULT_BUFFER_SIZE	0x1fffe


/* Bits in Mask to virtualMIDICreatePortEx2 */
/* TE_VM_FLAGS_PARSE_RX tells the driver to always provide valid preparsed MIDI-commands either via Callback or via virtualMIDIGetData */
#define TE_VM_FLAGS_PARSE_RX			(1)
/* TE_VM_FLAGS_PARSE_TX tells the driver to parse all data received via virtualMIDISendData */
#define TE_VM_FLAGS_PARSE_TX			(2)
/* TE_VM_FLAGS_INSTANTIATE_RX_ONLY - Only the "midi-out" part of the port is created */
#define TE_VM_FLAGS_INSTANTIATE_RX_ONLY		(4)
/* TE_VM_FLAGS_INSTANTIATE_TX_ONLY - Only the "midi-in" part of the port is created */
#define TE_VM_FLAGS_INSTANTIATE_TX_ONLY		(8)
/* TE_VM_FLAGS_INSTANTIATE_BOTH - a bidirectional port is created */
#define TE_VM_FLAGS_INSTANTIATE_BOTH		(12)

#define TE_VM_FLAGS_SUPPORTED			( TE_VM_FLAGS_PARSE_RX | TE_VM_FLAGS_PARSE_TX | TE_VM_FLAGS_INSTANTIATE_RX_ONLY | TE_VM_FLAGS_INSTANTIATE_TX_ONLY )

/*
 * Pointer to an opened teVirtualMIDI-port.  The data referenced by this pointer is for internal
 * use only, considered to be opaque and can change with each revision of the DLL.
 */
typedef struct _VM_MIDI_PORT VM_MIDI_PORT, *LPVM_MIDI_PORT;



/* Callback interface.  This callback is called by the driver/interface-dll for a packet of MIDI-data that is received from the driver
 * by the application using the virtual MIDI-port.
 *
 * This callback is called in an arbitrary thread-context - so make sure you have all your locking in order!
 *
 * If you have created the virtual-MIDI-port and specified TE_VM_FLAGS_PARSE_RX in the flags parameter, you will
 * receive a fully valid, preparsed MIDI-command with each callback.  The maximum size of data will be the amount
 * you specified in maxSysexLength.  Invalid commands or Sysex-commands with a length in excess of maxSysexLength
 * will be discarded and not forwarded to you.  Realtime-MIDI-commands will never be "intermingled" with other
 * commands (either normal or Sysex) in this mode.  If a realtime-MIDI-command is detected, it is sent to the
 * application before the command that it was intermingled with.
 *
 * In case of the driver being deactivated, the callback is called one time with a midiDataBytes==NULL and
 * length==zero, either the driver has been disabled, or another application using the driver has started
 * the installation of a newer driver-version
 *
 * You can throttle the speed of your virtualMIDI-port by not returning immediately from
 * this callback after you have taken care of the data received.
 *
 * If you want to throttle to 31250 bps for example, you need to place this line
 * before you return from your callback-function:
 *
 * Sleep( length * 10 * 1000) / 31250 );
 */
typedef void ( CALLBACK *LPVM_MIDI_DATA_CB )( LPVM_MIDI_PORT midiPort, LPBYTE midiDataBytes, DWORD length, DWORD_PTR dwCallbackInstance );


/* D E P R E C A T E D   -   do not use for new implementations */
LPVM_MIDI_PORT CALLBACK virtualMIDICreatePort( LPCWSTR portName, LPVM_MIDI_DATA_CB callback, DWORD_PTR dwCallbackInstance );
/* D E P R E C A T E D   -   do not use for new implementations */
LPVM_MIDI_PORT CALLBACK virtualMIDICreatePortEx( LPCWSTR portName, LPVM_MIDI_DATA_CB callback, DWORD_PTR dwCallbackInstance, DWORD maxSysexLength );


/* virtualMIDICreatePortEx2 - this is the current intended function to create a virtual MIDI-port.
 *
 * You can specify a name for the device to be created. Each named port can only exist once on a system.
 *
 * When the application terminates, the port will be deleted (or if the public front-end of the port is already in use by a DAW-application,
 * it will become inactive - giving back apropriate errors to the application using this port.
 *
 * In addition to the name, you can supply a callback-interface, which will be called for all MIDI-data received by the virtual-midi port.
 * You can also provide instance-data, which will also be handed back within the callback, to have the ability to reference port-specific
 * data-structures within your application.
 *
 * If you specify "NULL" for the callback function, you will not receive any callback, but can call the blocking function "virtualMIDIGetData"
 * to retrieve received MIDI-data/commands.  This is especially useful if one wants to interface this library to managed code like .NET or
 * Java, where callbacks into managed code are potentially complex or dangerous.  A call to virtualMIDIGetData when a callback has been
 * set during the creation will return with "ERROR_INVALID_FUNCTION".
 *
 * If you specified TE_VM_FLAGS_PARSE_RX in the flags parameter, you will always get one fully valid, preparsed MIDI-command in each callback.
 * In maxSysexLength you should specify a value that is large enough for the maximum size of Sysex that you expect to receive.  Sysex-commands
 * larger than the value specified here will be discarded and not sent to the user.  Realtime-MIDI-commands will never be "intermingled" with
 * other commands (either normal or Sysex) in this mode.  If a realtime-MIDI-command is detected, it is sent to the application before the
 * command that it was intermingled with.
 *
 * If you specify a maxSysexLength smaller than 2, you will receive fully valid preparsed MIDI-commands, but no Sysex-commands, since a
 * Sysex-command must be at least composed of 0xf0 + 0xf7 (start and end of sysex).  Since the parser will never be able to construct a
 * valid Sysex, you will receive none - but all other MIDI-commands will be parsed out and sent to you.
 *
 * When a NULL-pointer is handed back to the application, creation failed.  You can check GetLastError() to find out the specific problem
 * why the port could not be created.
 */
LPVM_MIDI_PORT CALLBACK virtualMIDICreatePortEx2( LPCWSTR portName, LPVM_MIDI_DATA_CB callback, DWORD_PTR dwCallbackInstance, DWORD maxSysexLength, DWORD flags );

/* virtualMIDICreatePortEx3
 *
 * This version of the function adds the ability to provide two pointers to GUIDs that can be used to set the manufacturer and product guids
 * that an application using the public port can retrieve using midiInGetDevCaps or midiOutGetDevCaps with the extended device-capability-
 * structures (MIDIINCAPS2 and MIDIOUTCAPS2).  If those pointers are set to NULL, the default-guids of the teVirtualMIDI-driver will be used.
 */
LPVM_MIDI_PORT CALLBACK virtualMIDICreatePortEx3( LPCWSTR portName, LPVM_MIDI_DATA_CB callback, DWORD_PTR dwCallbackInstance, DWORD maxSysexLength, DWORD flags, GUID *manufacturer, GUID *product );



/* With this function, you can close a virtual MIDI-port again, after you have instanciated it.
 *
 * After the return from this function, no more callbacks will be received.
 *
 * Beware: do not call this function from within the midi-port-data-callback.  This may result in a deadlock!
 */
void CALLBACK virtualMIDIClosePort( LPVM_MIDI_PORT midiPort );



/* With this function you can send a buffer of MIDI-data to the driver / the application that opened the virtual-MIDI-port.
 * If this function returns false, you may check GetLastError() to find out what caused the problem.
 *
 * This function should always be called with a single complete and valid midi-command (1-3 octets, or possibly more
 * for sysex).  Sysex-commands should not be split!  Realtime-MIDI-commands shall not be intermingled with other MIDI-
 * commands, but sent seperately!
 *
 * The data-size that can be used to send data to the virtual ports may be limited in size to prevent
 * an erratic application to allocate too much of the limited kernel-memory thus interfering with
 * system-stability.  The current limit is 512kb.
 */
BOOL CALLBACK virtualMIDISendData( LPVM_MIDI_PORT midiPort, LPBYTE midiDataBytes, DWORD length );



/* With this function you can use virtualMIDI without usage of callbacks.  This is especially interesting
 * if you want to interface the DLL to managed environments like Java or .NET where callbacks from native
 * to managed code are more complex.
 *
 * To use it, you need to open a virtualMIDI-port specifying NULL as callback.  If you have specified a
 * callback when opening the port, this function will fail - you cannot mix callbacks & reading via this
 * function.
 *
 * You need to provide a buffer large enough to retrieve the amount of data available.  Otherwise the
 * function will fail and return to you the necessary size in the length parameter.  If you specify
 * midiDataBytes to be NULL, the function will succeed but only return the size of buffer necessary
 * to retrieve the next MIDI-packet.
 *
 * virtualMIDIGetData will block until a complete block of data is available.  Depending on the fact if
 * you have specified to parse data into valid commands or just chunks of unprocessed data, you will
 * either receive the unparsed chunk (possibly containing multiple MIDI-commands), or a single, fully
 * valid MIDI-command.  In both cases, the length parameter will be filled with the length of data retrieved.
 *
 * You may only call virtualMIDIGetData once concurrently.  A call to this function will fail if another
 * call to this function is still not completed.
 */
BOOL CALLBACK virtualMIDIGetData( LPVM_MIDI_PORT midiPort, LPBYTE midiDataBytes, PDWORD length );



/* With this function an application can find out the process-ids of all applications
 * that are currently using this virtual MIDI port
 * A pointer to an array of ULONG64s must be supplied.  Currently no more than 16 process ids are returned
 * Before calling the length is the size of the buffer provided by the application in bytes
 * After calling the length is the number of process-ids returned times sizeof(ULONG64)
 */
BOOL CALLBACK virtualMIDIGetProcesses( LPVM_MIDI_PORT midiPort, ULONG64 *processIds, PDWORD length );



/* With this function you can abort the created midiport.  This may be useful in case you want
 * to use the virtualMIDIGetData function which is blocking until it gets data.  After this
 * call has been issued, the port will be shut-down and any further call (other than virtualMIDIClosePort)
 * will fail
 */
BOOL CALLBACK virtualMIDIShutdown( LPVM_MIDI_PORT midiPort );



/* With this function you can retrieve the version of the driver that you are using.
 * In addition you will receive the version-number as a wide-string constant as return-value.
 */
LPCWSTR CALLBACK virtualMIDIGetVersion( PWORD major, PWORD minor, PWORD release, PWORD build );



/* With this function you can retrieve the version of the driver that you are using.
 * In addition you will receive the version-number as a wide-string constant as return-value.
 */
LPCWSTR CALLBACK virtualMIDIGetDriverVersion( PWORD major, PWORD minor, PWORD release, PWORD build );



/* With this function logging can be activated into DbgView.
 * Please specify a bitmask made up form binary "or"ed values from TE_VM_LOGGING_XXX
 */
DWORD CALLBACK virtualMIDILogging( DWORD logMask );


#ifdef __cplusplus
	}
#endif

#endif
