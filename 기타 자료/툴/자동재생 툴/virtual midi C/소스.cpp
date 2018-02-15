#define _CRT_SECURE_NO_WARNINGS

#include <stdio.h>
#include <conio.h>
#include <time.h>
#include "teVirtualMIDI.h"

#define MAX_SYSEX_BUFFER	65535

FILE *file;

void fileEnd() {
	if (file != NULL) {
		fclose(file);
	}
}

long time_log = 0;
void timeLog() {
	

	long currentTime = clock();
	long time = currentTime - time_log;
	if (time_log == 0) {
		fprintf(file, "d 500\n");
		printf("d 500\n");
	}else if (time > 0) {
		fprintf(file, "d %d\n", time);
		printf("d %d\n", time);
	}
	time_log = currentTime;
}

void pad(int x, int y, boolean t) {

	timeLog();

	if (t) {
		fprintf(file, "o %d %d\n", x, y);
		printf("o %d %d\n", x, y);
	} else {
		fprintf(file, "f %d %d\n", x, y);
		printf("f %d %d\n", x, y);
	}
}

void chain(int c) {

	timeLog();

	fprintf(file, "chain %d\n", c);
	printf("chain %d\n", c);
}

void CALLBACK teVMCallback(LPVM_MIDI_PORT midiPort, LPBYTE midiDataBytes, DWORD length, DWORD_PTR dwCallbackInstance) {


	int ch = (midiDataBytes[0] & 0xFF) % 0x10;
	int comm = (midiDataBytes[0] & 0xFF) / 0x10;
	int note = midiDataBytes[1];
	int velo = midiDataBytes[2];

	int x = 0;
	int y = 0;

	if (note >= 36 && note <= 67) {
		x = (67 - note) / 4 + 1;
		y = 4 - (67 - note) % 4;
	} else if (note >= 68 && note <= 99) {
		x = (99 - note) / 4 + 1;
		y = 8 - (99 - note) % 4;
	} else if (note >= 100 && note <= 107) {
		x = note - 99;
		y = 9;
	} else return;

	if (comm == 8 || comm == 9) {
		if (y >= 1 && y <= 8)
			pad(x, y, comm == 9);
		if (y == 9 && comm == 9)
			chain(x);
	}
}

int main(int argc, const char *argv[]) {
	printf("Unipad AutoPlay Tool 1.2\n");
	printf("using dll-version:    %ws\n", virtualMIDIGetVersion(NULL, NULL, NULL, NULL));
	printf("using driver-version: %ws\n", virtualMIDIGetDriverVersion(NULL, NULL, NULL, NULL));
	file = fopen("autoPlay", "w");

	LPVM_MIDI_PORT port = virtualMIDICreatePortEx2(L"Unipad AutoPlay tool", teVMCallback, 0, MAX_SYSEX_BUFFER, TE_VM_FLAGS_PARSE_RX);
	if (!port) {
		printf("could not create port\n");
		printf("program ended\n");
		_getch();
		return -1;
	}
	_getch();

	virtualMIDIClosePort(port);
	printf("program ended\n");
	_getch();
}