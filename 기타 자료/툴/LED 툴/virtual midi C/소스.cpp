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

void chain(int y, int velo, boolean t) {

	timeLog();

	if (file != NULL) {
		if (t)
			fprintf(file, "o * %d a %d\n", y, velo);
		else
			fprintf(file, "f * %d\n", y);
	}
	else {
		if (t)
			sprintf(tmp, "%so * %d a %d\n", tmp, y, velo);
		else
			sprintf(tmp, "%sf * %d\n", tmp, y);
	}
}

int getChainNum(int note) {
	int y = 0;

	switch (note) {
	case 100:
		y = 1;
		break;
	case 101:
		y = 2;
		break;
	case 102:
		y = 3;
		break;
	case 103:
		y = 4;
		break;
	case 104:
		y = 5;
		break;
	case 105:
		y = 6;
		break;
	case 106:
		y = 7;
		break;
	case 107:
		y = 8;
		break;
	case 123:
		y = 9;
		break;
	case 122:
		y = 10;
		break;
	case 121:
		y = 11;
		break;
	case 120:
		y = 12;
		break;
	case 119:
		y = 13;
		break;
	case 118:
		y = 14;
		break;
	case 117:
		y = 15;
		break;
	case 116:
		y = 16;
		break;
	case 115:
		y = 17;
		break;
	case 114:
		y = 18;
		break;
	case 113:
		y = 19;
		break;
	case 112:
		y = 20;
		break;
	case 111:
		y = 21;
		break;
	case 110:
		y = 22;
		break;
	case 109:
		y = 23;
		break;
	case 108:
		y = 24;
		break;
	case 28:
		y = 25;
		break;
	case 29:
		y = 26;
		break;
	case 30:
		y = 27;
		break;
	case 31:
		y = 28;
		break;
	case 32:
		y = 29;
		break;
	case 33:
		y = 30;
		break;
	case 34:
		y = 31;
		break;
	case 35:
		y = 32;
		break;
	}
	return y;
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
	} else if ((note >= 100 && note <= 107)|| (note >= 116 && note <= 123)|| (note >= 108 && note <= 115)|| (note >= 28 && note <= 35)) {
		
		x = -1;
		y = getChainNum(note);
	} else return;

	if (ch == 0) {
		if (comm == 9) {
			fileEnd();
			if (y == 9) {
				/*c = x;
				printf("chain = %d\n", c);*/
			} else {
				char filename[20];
				sprintf(filename, "%d %d %d", c, x, y);
				printf("%s [", filename);
				file = fopen(filename, "w");
				fprintf(file, tmp);
				tmp[0] = '\0';
			}
		} else if (comm == 8 && false) {
			fileEnd();
		}
	} else if (comm == 8 || comm == 9) {
		if (x == -1) {
			chain(y, velo, comm == 9);
			first = false;
		} else {
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