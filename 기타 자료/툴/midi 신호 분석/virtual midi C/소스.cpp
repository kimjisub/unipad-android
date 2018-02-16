#define _CRT_SECURE_NO_WARNINGS

#include <stdio.h>
#include <conio.h>
#include <time.h>
#include "teVirtualMIDI.h"

#define MAX_SYSEX_BUFFER	65535

FILE *file;
boolean first = true;
char tmp[1000];

void fileEnd() {
	if (file != NULL) {
		fclose(file);
		printf("]\n");
		file = NULL;
		first = true;
	}
}

long time_log = 0;
void timeLog() {
	long currentTime = clock();
	long time = currentTime - time_log;
	if (time > 0) {
		//printf("d %d\r\n", currentTime - time_log);
		putchar('.');
		if (!first) {
			if (file != NULL)
				fprintf(file, "d %d\n", currentTime - time_log);
			else
				sprintf(tmp, "%sd %d\n", tmp, currentTime - time_log);

		}

	}
	time_log = currentTime;
}

void pad(int x, int y, int velo, boolean t) {

	timeLog();

	if (file != NULL) {
		if (t)
			fprintf(file, "o %d %d a %d\n", x, y, velo);
		else
			fprintf(file, "f %d %d\n", x, y);
	} else {
		if (t)
			sprintf(tmp, "%so %d %d a %d\n", tmp, x, y, velo);
		else
			sprintf(tmp, "%sf %d %d\n", tmp, x, y);
	}
}

int c = 1;
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

	if (ch == 0) {
		if (comm == 9) {
			fileEnd();
			if (y == 9) {
				/*c = x;
				printf("chain = %d\n", c);*/
			} else {
				char filename[20];
				sprintf(filename, "%d %d %d 1", c, x, y);
				printf("%s [", filename);
				file = fopen(filename, "w");
				fprintf(file, tmp);
				tmp[0] = '\0';
			}
		} else if (comm == 8 && false) {
			fileEnd();
		}
	} else if (comm == 8 || comm == 9) {
		if (y == 9)
			;
		else {
			pad(x, y, velo, comm == 9);
			first = false;
		}
	}
}

int main(int argc, const char *argv[]) {
	printf("Unipad LED Tool 1.2\n");
	printf("using dll-version:    %ws\n", virtualMIDIGetVersion(NULL, NULL, NULL, NULL));
	printf("using driver-version: %ws\n", virtualMIDIGetDriverVersion(NULL, NULL, NULL, NULL));

	LPVM_MIDI_PORT port = virtualMIDICreatePortEx2(L"Unipad LED tool", teVMCallback, 0, MAX_SYSEX_BUFFER, TE_VM_FLAGS_PARSE_RX);
	if (!port) {
		printf("could not create port\n");
		printf("program ended\n");
		_getch();
		return -1;
	}
	while (true) {
		int get = _getch();
		fileEnd();
		if ('1' <= get && get <= '8') {
			c = get - '0';
			printf("chain = %d\n", c);
		} else if (get == '\r' || get == '\n') {
		} else
			break;
	}
	virtualMIDIClosePort(port);
	printf("program ended\n");
	_getch();
}