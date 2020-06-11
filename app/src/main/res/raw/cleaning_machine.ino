//#include <Servo.h>
#include <timer.h>
#include <AccelStepper.h>
//#include <UltraDistSensor.h>

// https://github.com/contrem/arduino-timer





//------ Sensor Ultrason  -----------------------------
#define PINO_LEITOR_ULTRASON_1_TRIGGER 	53 // 53 // Termometro 2  53 50
#define PINO_LEITOR_ULTRASON_1_ECHO 	52 // 53 // Termometro 3  52 41


#define PINO_LEITOR_ULTRASON_2_TRIGGER 	22 // 39 // Alcool Gel
#define PINO_LEITOR_ULTRASON_2_ECHO 	40 // 39 // Alcool Gel





//------ Bomba de alcool -----------------------------
#define BOMBA_ALCOOL 	10
#define	BOMBA_SPRAY		8
#define	BOMBA_3			0
#define	BOMBA_4			0

//------ Leds placa de teste  -----------------------------
#define LED_1 42
#define LED_2 43
#define LED_3 44
#define LED_4 45


//------ Leds RGB  -----------------------------
#define LED_R 	12
#define LED_G 	11
#define LED_B 	9



//------ Outros  -----------------------------
//#define LIGA_TABLET       32

//#define PIR_SENSOR 		  ????? // Marcus, não esta sendo utilizado


//------ Entrada/Saida  -----------------------------
#define ENTRADA_SENSOR 	7
#define SAIDA_SENSOR 	39


// TODO: definir
#define ALARM_1 			0   // MARCUS, falta definir


//-------------------------------------------------
//=====> Variaveis comuns ao protocolo e ao Arduino
//-------------------------------------------------
// Definições vinculadas ao TABLET
#define SENSOR_PRESENCA 1
#define SENSOR_ENTRADA  2
#define SENSOR_SAIDA    3
#define SENSOR_ALCOOL   4


//-------------------------------------------------
//=====> Timer Arduino
//-------------------------------------------------
auto timerManager = timer_create_default(); 
#define	FREQUENCIA_TIMER_BOMBA			100 // 100ms

#define	FREQUENCIA_LEITURA_ULTRASOM   	50

int distanciaLidaSensor1, distanciaLidaSensor2;
int distanciaMediaSensor1, distanciaMediaSensor2;

int suspendeLeituraDosSensoresUltrason = 0;

int tempoBombaAlcoolLigada = 2000;
int tempoBombaSprayLigada = 0;
int tempoBomba3Ligada = 0;
int passosBombaStepperLigada = 0;

int timeoutBombaAlcool=0;
int timeoutBombaSpray=0;
int timeoutBomba3=0;
int timeoutBomba4=0;


//----------------------------------------------
//=====> Bomba Stepper
//----------------------------------------------
#define STEP_PIN 			33
#define DIR_PIN 			31
#define ENABLE_STEP_PIN 	29
#define RESET_STEP_PIN  	30
AccelStepper stepper (1, STEP_PIN, DIR_PIN);


//int steps=250;
int bombaStepperLigada = 0;
//bool processaBombaStepper(void *argument)
//{
//	if ( bombaStepperLigada ) {
//		if (stepper.distanceToGo() != 0) {
//			stepper.run();
//		} else {
//			digitalWrite(ENABLE_STEP_PIN, HIGH);
//			bombaStepperLigada = 0;
//			digitalWrite(LED_3, LOW);  // ANANA
//		}
//	}
//	return (true);  // to repeat the action - false to stop
//}
void ligaBombaStepper(void)
{
	digitalWrite(ENABLE_STEP_PIN, LOW);
	stepper.move(passosBombaStepperLigada);
	bombaStepperLigada = 1;
	digitalWrite(LED_3, HIGH);  // ANANA
}



//----------------------------------------------
//=====> Bomba Alcool
//----------------------------------------------
bool processaBombaAlcool(void *argument)
{	
	if ( timeoutBombaAlcool == 0 ) return(true);
	
	if ( timeoutBombaAlcool > 0 ) {
		timeoutBombaAlcool -= FREQUENCIA_TIMER_BOMBA;

		if ( timeoutBombaAlcool <= 0 ) {
			digitalWrite(BOMBA_ALCOOL, LOW);
			timeoutBombaAlcool = 0;
		}
	}
	return (true);  // to repeat the action - false to stop
}

void ligaBombaAlcool(void)
{	
	if ( timeoutBombaAlcool == 0) {
		digitalWrite(BOMBA_ALCOOL, HIGH);
		timeoutBombaAlcool = tempoBombaAlcoolLigada;
	}
}

int bombaSprayLigada=0;
//----------------------------------------------
//=====> Bomba Spray
//----------------------------------------------
bool processaBombaSpray(void *argument)
{	
	
	if ( bombaSprayLigada == 0) return(true);
	
	if ( timeoutBombaSpray >= 0 ) {
		timeoutBombaSpray -= FREQUENCIA_TIMER_BOMBA;

		if ( timeoutBombaSpray <= 0 ) {
			digitalWrite(BOMBA_SPRAY, LOW);
			digitalWrite(LED_4, LOW);  // ANANA
			timeoutBombaSpray = 0;
			bombaSprayLigada = 0;
		}
	}
	return (true);  // to repeat the action - false to stop
}

void ligaBombaSpray(void)
{	
	if ( bombaSprayLigada == 0) {
		bombaSprayLigada = 1;
		digitalWrite(BOMBA_SPRAY, HIGH);
		digitalWrite(LED_4, HIGH);  // ANANA
		timeoutBombaSpray = tempoBombaSprayLigada;
	}
}



//========================================================================================
//=====> Sensor Ultrasom
//========================================================================================


//UltraDistSensor sensorUltrasom_1;
//UltraDistSensor sensorUltrasom_2;
//UltraDistSensor sensorUltrasom_3;

#define	STATUS_PRESENTE   1
#define	STATUS_AUSENTE    0

#define QTD_LEITURAS_CONSECUTIVAS_ULTRASON    2 // Lendo a uma taxa de 100ms perceberá a mudança em 0,5 segundos


int distanciaPresencaSensor1 = 5;	// em CM
int distanciaPresencaSensor2 = 5;	// em CM
int distanciaPresencaSensor3 = 5;	// em CM
int distanciaPresencaSensor4 = 5;	// em CM

	
int statusSensorUltrason_1=STATUS_AUSENTE;
int statusSensorUltrason_2=STATUS_AUSENTE;
int statusSensorUltrason_3=STATUS_AUSENTE;

void fazLeituraUltrasom_1(void);
void fazLeituraUltrasom_2(void);
void fazLeituraUltrasom_3(void);

//
//--- Chamada pelo timer
//
bool processaLeitoresUltrasom(void *argument) // optional argument given to in/at/every 
{	static bool flag=0;


	flag = ! flag;	


	// TODO: Ver se da problema lendo tudo da mesma vez
	if ( flag) {
    	if ( PINO_LEITOR_ULTRASON_2_TRIGGER ) fazLeituraUltrasom_1();
	} else {
    	if ( PINO_LEITOR_ULTRASON_1_TRIGGER ) fazLeituraUltrasom_2();
	}
	
	return (true);  // to repeat the action - false to stop
}

#define	TIMEOUT_PULSE_IN   3000  // 5882 microsegundos * 340metros por segundo = 2 metros ==> 5882 * 2  (5,8ms)


#define AMOSTRAGENS   5
#define DISTANCIA_LIMITE_SENSOR_1	20
#define DISTANCIA_LIMITE_SENSOR_2	40

int normalizaDistancia1(int distancia, int distanciaMaxima)
{
	static int vetorValores[AMOSTRAGENS+1];
	static int indAmostra=0;
	static int somaAmostras=0;
	static int mediaAmostras=0;
	static int descartaAmostras=0;

	if ( distancia > distanciaMaxima ) {
		distancia = distanciaMaxima;
	}
	
	if ( descartaAmostras == 0 ) {
		vetorValores[indAmostra] = distancia;
		somaAmostras += distancia;
		indAmostra++;
		mediaAmostras = somaAmostras / indAmostra;
		if ( indAmostra >= AMOSTRAGENS) {
			descartaAmostras = 1;
		}
	}else {
		if ( indAmostra >= AMOSTRAGENS ) {
			// vou voltar pro primeiro
			indAmostra = 0;
		}
		somaAmostras -=  vetorValores[indAmostra];
		vetorValores[indAmostra] = distancia;
		somaAmostras += distancia;
		indAmostra++;
		mediaAmostras = somaAmostras / AMOSTRAGENS;
	}

	return(mediaAmostras);
}

// ---------- Leitor 1 ------------------
void fazLeituraUltrasom_1(void)
{
	static int contagensAcima=0;
	static int contagensAbaixo=0;

	int duration,  distanceConsiderada;  

	if ( PINO_LEITOR_ULTRASON_1_TRIGGER && (suspendeLeituraDosSensoresUltrason == 0)) {
	    digitalWrite(PINO_LEITOR_ULTRASON_1_TRIGGER, LOW);  
	    delayMicroseconds(2);  
	    digitalWrite(PINO_LEITOR_ULTRASON_1_TRIGGER, HIGH);  
	    delayMicroseconds(10);  
	    digitalWrite(PINO_LEITOR_ULTRASON_1_TRIGGER, LOW);  
	    duration = pulseIn(PINO_LEITOR_ULTRASON_1_ECHO, HIGH, TIMEOUT_PULSE_IN);  

		if (duration == 0 ) {
			distanciaLidaSensor1 = 0;
			distanceConsiderada = DISTANCIA_LIMITE_SENSOR_1;
		} else {
		    // Convert in CM  
		    distanceConsiderada = duration * 0.034 / 2;  // Speed of sound wave divided by 2 (go and back)
			distanciaLidaSensor1 = distanceConsiderada;
		}

		distanciaMediaSensor1  = normalizaDistancia1( distanceConsiderada, DISTANCIA_LIMITE_SENSOR_1);

		if ( distanciaMediaSensor1 <  distanciaPresencaSensor1  ) { 
			contagensAcima=0;
			contagensAbaixo++;
			if ( contagensAbaixo > QTD_LEITURAS_CONSECUTIVAS_ULTRASON ) {
				statusSensorUltrason_1 = STATUS_PRESENTE;
			}
		} else {
			contagensAbaixo=0;
			contagensAcima++;
			if ( contagensAcima > QTD_LEITURAS_CONSECUTIVAS_ULTRASON  ) {
				statusSensorUltrason_1 = STATUS_AUSENTE;
			}
		}

	}		
}



int normalizaDistancia2(int distancia, int distanciaMaxima)
{
	static int vetorValores[AMOSTRAGENS+1];
	static int indAmostra=0;
	static int somaAmostras=0;
	static int mediaAmostras=0;
	static int descartaAmostras=0;

	if ( distancia > distanciaMaxima ) {
		distancia = distanciaMaxima;
	}
	
	if ( descartaAmostras == 0 ) {
		vetorValores[indAmostra] = distancia;
		somaAmostras += distancia;
		indAmostra++;
		mediaAmostras = somaAmostras / indAmostra;
		if ( indAmostra >= AMOSTRAGENS) {
			descartaAmostras = 1;
		}
	}else {
		if ( indAmostra >= AMOSTRAGENS ) {
			// vou voltar pro primeiro
			indAmostra = 0;
		}
		somaAmostras -=  vetorValores[indAmostra];
		vetorValores[indAmostra] = distancia;
		somaAmostras += distancia;
		indAmostra++;
		mediaAmostras = somaAmostras / AMOSTRAGENS;
	}

	return(mediaAmostras);
}

// ---------- Leitor 2 ------------------
void fazLeituraUltrasom_2(void)
{
	static int contagensAcima=0;
	static int contagensAbaixo=0;

	int duration,  distanceConsiderada;  

	if ( PINO_LEITOR_ULTRASON_2_TRIGGER && (suspendeLeituraDosSensoresUltrason == 0) ) {
	    digitalWrite(PINO_LEITOR_ULTRASON_2_TRIGGER, LOW);  
	    delayMicroseconds(2);  
	    digitalWrite(PINO_LEITOR_ULTRASON_2_TRIGGER, HIGH);  
	    delayMicroseconds(10);  
	    digitalWrite(PINO_LEITOR_ULTRASON_2_TRIGGER, LOW);  
	    duration = pulseIn(PINO_LEITOR_ULTRASON_2_ECHO, HIGH, TIMEOUT_PULSE_IN);  
	    
		if (duration == 0 ) {
			distanciaLidaSensor2 = 0;
			distanceConsiderada = DISTANCIA_LIMITE_SENSOR_2;
		} else {
		    // Convert in CM  
		    distanceConsiderada = duration * 0.034 / 2;  // Speed of sound wave divided by 2 (go and back)
			distanciaLidaSensor2 = distanceConsiderada;
		}

		distanciaMediaSensor2  = normalizaDistancia2( distanceConsiderada, DISTANCIA_LIMITE_SENSOR_2);

		if ( distanciaMediaSensor2 <  distanciaPresencaSensor2  ) {
			contagensAcima=0;
			contagensAbaixo++;
			if ( contagensAbaixo > QTD_LEITURAS_CONSECUTIVAS_ULTRASON ) {
				statusSensorUltrason_2 = STATUS_PRESENTE;
			}
		} else {
			contagensAbaixo=0;
			contagensAcima++;
			if ( contagensAcima > QTD_LEITURAS_CONSECUTIVAS_ULTRASON ) {
				statusSensorUltrason_2 = STATUS_AUSENTE;
			}
		}
	}		
}

// ---------- Leitor 3 ------------------
void fazLeituraUltrasom_3(void)
{	
	// TODO: Copiar de 1 quando OK
}


void setupStepper() {
  digitalWrite(ENABLE_STEP_PIN, LOW);  // ZU digitalWrite(ENABLE_STEP_PIN, HIGH); // Desligada
  digitalWrite(RESET_STEP_PIN, HIGH);
  stepper.setMaxSpeed(300);
  stepper.setAcceleration(800);
}

//========================================================================================
// setup
//========================================================================================
void setup()
{
	zeraTudo();


	if ( ENTRADA_SENSOR   ) pinMode(ENTRADA_SENSOR, INPUT_PULLUP);
	if ( SAIDA_SENSOR     ) pinMode(SAIDA_SENSOR, INPUT_PULLUP);
	
//	if ( PIR_SENSOR       ) pinMode(PIR_SENSOR, INPUT_PULLUP);
//	if ( LIGA_BOMBA_PE    ) pinMode(LIGA_BOMBA_PE, OUTPUT);
//	if ( LIGA_TABLET      ) pinMode(LIGA_TABLET, OUTPUT);

	//--- Leds placa de teste
	if ( LED_1 ) pinMode(LED_1, OUTPUT);
	if ( LED_2 ) pinMode(LED_2, OUTPUT);
	if ( LED_3 ) pinMode(LED_3, OUTPUT);
	if ( LED_4 ) pinMode(LED_4, OUTPUT);

	if ( LED_R ) pinMode(LED_R, OUTPUT);
	if ( LED_G ) pinMode(LED_G, OUTPUT);
	if ( LED_B ) pinMode(LED_B, OUTPUT);


	//=====> Serial para conversar com Tablet
	Serial.begin(115200);   //Sets the baud for serial data transmission
	
	
	//=====> Bomba Alcool
	if ( BOMBA_ALCOOL ) {
		pinMode(BOMBA_ALCOOL, OUTPUT);
		digitalWrite(BOMBA_ALCOOL, LOW);
		timerManager.every( FREQUENCIA_TIMER_BOMBA, processaBombaAlcool); // tempo em ms
	}

	//=====> Bomba 2
	if ( BOMBA_SPRAY ) {
		pinMode(BOMBA_SPRAY, OUTPUT);
		digitalWrite(BOMBA_SPRAY, LOW);
		timerManager.every( FREQUENCIA_TIMER_BOMBA, processaBombaSpray); // tempo em ms
	}

	//=====> Bomba Stepper
	if ( STEP_PIN ) {
		pinMode(STEP_PIN, OUTPUT);
		pinMode(DIR_PIN, OUTPUT);
		pinMode(ENABLE_STEP_PIN, OUTPUT);
		pinMode(RESET_STEP_PIN, OUTPUT);
		setupStepper();
	}

	timerManager.every( FREQUENCIA_LEITURA_ULTRASOM, processaLeitoresUltrasom); // tempo em ms


	if ( LED_BUILTIN  					) pinMode(LED_BUILTIN, OUTPUT);

	if ( PINO_LEITOR_ULTRASON_1_TRIGGER ) pinMode(PINO_LEITOR_ULTRASON_1_TRIGGER, OUTPUT);  
    if ( PINO_LEITOR_ULTRASON_1_ECHO    ) pinMode(PINO_LEITOR_ULTRASON_1_ECHO, INPUT);  

	if ( PINO_LEITOR_ULTRASON_2_TRIGGER ) pinMode(PINO_LEITOR_ULTRASON_2_TRIGGER, OUTPUT);  
    if ( PINO_LEITOR_ULTRASON_2_ECHO    ) pinMode(PINO_LEITOR_ULTRASON_2_ECHO, INPUT);  

}




//========================================================================================
// readSensor
//========================================================================================
int readSensor(int sensor) 
{
	//-------- Versão para funcionar em placas COM sensores	
	if ( sensor == SENSOR_PRESENCA ) {
		if ( statusSensorUltrason_1 == STATUS_PRESENTE ) {
			return (1); // perto
		} else {
			return (0); // longe
		}
	} else if ( sensor == SENSOR_ALCOOL ) {
		if ( statusSensorUltrason_2 == STATUS_PRESENTE ) {
			return (1);
		} else {
			return (0);
		}
	} else if ( sensor == SENSOR_ENTRADA ) {
		if (digitalRead(ENTRADA_SENSOR) == HIGH) {
			return (1);
		} else {
			return (0);
		}
	} else if ( sensor == SENSOR_SAIDA ) {
		if (digitalRead(SAIDA_SENSOR) == HIGH) {
			return (1);
		} else {
			return (0);
		}
	}
	return (0);
}


//========================================================================================
// readSensorAnalogico
//========================================================================================
float readSensorAnalogico(int sensor, int valorRefencia) 
{	static int semFebre = 0;
	int valor;
	//-------- Versão para funcionar em placas COM sensores	
	if ( sensor == 1 ) {
		if ( valorRefencia <= 2 ) {
			valor = random(373, 400);	// TODO: AJUSTAR
		} else {
			valor = random(360, 372);	// TODO: AJUSTAR
		}
		return(valor);
	} else if ( sensor == 2 ) {
		return(-1);
	} else {
		return(-1);
	}
	return (0);
}

//========================================================================================
// trataConfigDistanciaSensores
//========================================================================================
const char *trataConfigDistanciaSensores(int p1, int p2, int p3, int p4)
{	const char *strOk = "ok";

	distanciaPresencaSensor1 = p1;	
	distanciaPresencaSensor2 = p2;	
	distanciaPresencaSensor3 = p3;	
	distanciaPresencaSensor4 = p4;	

	return (strOk);
}

//========================================================================================
// trataConfigDasBombas
//========================================================================================
const char *trataConfigDasBombas(int p1, int p2, int p3, int p4)
{	const char *strOk = "ok";

	tempoBombaAlcoolLigada = p1;	
	tempoBombaSprayLigada = p2;	
	tempoBomba3Ligada = p3;	
	passosBombaStepperLigada = p4;	

	return (strOk);
}


//========================================================================================
// trata Leds
//========================================================================================
char *trataLed(int qual, int cor)
{

	static char strResp[20];
	int aceso = 0;
	
	digitalWrite(LED_R, LOW);
	digitalWrite(LED_G, LOW);
	digitalWrite(LED_B, LOW);
	
	sprintf(strResp, "%d", qual);


	if ( qual == 1)  {
		if ( cor & 0x1 ) {
			if ( LED_R ) digitalWrite(LED_R, HIGH);
			strcat(strResp, "R");
		} else {
			if ( LED_R ) digitalWrite(LED_R, LOW);
		}
	
		if ( cor & 0x2 ) {
			if ( LED_G ) digitalWrite(LED_G, HIGH );
			strcat(strResp, "G");
		} else {
			if ( LED_G ) digitalWrite(LED_G, LOW);
		}
	
		if ( cor & 0x4 ) {
			if ( LED_B ) digitalWrite(LED_B, HIGH);
			strcat(strResp, "B");
		} else {
			if ( LED_B ) digitalWrite(LED_B, LOW);
		}
	
		if ( (cor & 0x7) == 0 ) {
			strcat(strResp, "off");
		}
	}

	return (strResp);
}


//========================================================================================
// trata alarmes
//========================================================================================
char *trataAlarm(int qual, int status)
{ 
	static char strResp[20];
	int aceso = 0;

	strcpy(strResp, "NAO IMPLEMENTADO");

	switch ( qual ) {
		case 1 : 
			if ( ALARM_1 != 0 ) {
				digitalWrite(ALARM_1, (status)? HIGH :  LOW);
				sprintf(strResp, "ALARM%d %s", qual, (status) ? "ON" : "OFF");
			}
			break;

		default : break;
	}

	return (strResp);
}




//========================================================================================
// trata ejeção de produtos
//========================================================================================
char *trataEject(int produto, int valor)
{ 
	static char strResp[20];

	if ( produto == 1 ) {
		if ( STEP_PIN == 0 ) {
			ligaBombaAlcool();
		} else {
			ligaBombaStepper();
		}
	}

	if ( produto == 2 ) {
		ligaBombaSpray();
	}

	sprintf(strResp, "%d,%d", produto, valor);

	return (strResp);
}


//========================================================================================
// readBalanca
//========================================================================================
int readBalanca(int balanca) {
  if ( balanca == 1 ) return (0);
  if ( balanca == 2 ) return (0);
  if ( balanca == 3 ) return (0);
  return (0);
}


//========================================================================================
// loop principal
//========================================================================================
void loop()
{
	timerManager.tick(); // executa rotinas no timer

	if ( bombaStepperLigada ) {
		if (stepper.distanceToGo() != 0) {
		  stepper.run();
		} else {
		  // digitalWrite(ENABLE_STEP_PIN, HIGH); //ZU 
		  bombaStepperLigada=0;
		  digitalWrite(LED_3, LOW);  // ANANA
		}
	}
	
	if ( Serial.available() > 0) {
		processaSerial();
	}

}



//========================================================================================
// Interação com Android
//========================================================================================

#define AGUARDANDO_START       0
#define AGUARDANDO_ABRE_ASPAS  1
#define AGUARDANDO_TOKEN       2
#define AGUARDANDO_DOIS_PONTOS 3
#define AGUARDANDO_VALOR       4
#define AGUARDANDO_VIRGULA     5


void zeraNovoPacote(void);

#define CMD_RESTART     1
#define CMD_STATUS_RQ   3
#define CMD_EJECT       4
#define CMD_PLAY        5
#define CMD_LED         6
#define CMD_ALARM       7
#define CMD_CONFIG      8

#define ACTION_RESET    1
#define ACTION_ON       2
#define ACTION_OFF      3
#define ACTION_QUESTION 4
#define SIMULA5REAIS    5
#define SIMULA10REAIS   6
#define SIMULA20REAIS   7
#define SIMULA50REAIS   8




int reinicializando = 0;
int numPktResp = 0;

int flagValorNumerico = 0;


char strToken[30];
char strValor[30];
char strHora[30];

char strCmd[30];
char strAction[30];

int flagTokenValor = 0;
int packetNumber = 0;
int pacotesNaoReconhecidos = 0;
int iComando;
int iAction;

int erro = 0;
int statusPlay = 0;
int statusDemo = 0;
int fsmStateDemo = 0;
int valorTipo=0, valorP1 = 0, valorP2 = 0, valorP3, valorP4;



//========================================================================================
// Conjunto de funções para montar pacote de resposta para enviar para Tablet
//========================================================================================
char bufResposta[400];
int indBufResposta;

// ---------------------------------------------
void startResposta(const char *cmd) 
{
	numPktResp++;

	indBufResposta = 0;

	bufResposta[indBufResposta++] = '{';
	bufResposta[indBufResposta++] = '"';
	bufResposta[indBufResposta++] = 'c';
	bufResposta[indBufResposta++] = 'm';
	bufResposta[indBufResposta++] = 'd';
	bufResposta[indBufResposta++] = '"';
	bufResposta[indBufResposta++] = ':';
	bufResposta[indBufResposta++] = '"';
	while (*cmd) {
		bufResposta[indBufResposta++] = *cmd++;
	}
	bufResposta[indBufResposta++] = '"';
}

// ---------------------------------------------
void addStrResposta(const char *cmd, const char *param) 
{
	bufResposta[indBufResposta++] = ',';
	bufResposta[indBufResposta++] = '"';
	while (*cmd) {
		bufResposta[indBufResposta++] = *cmd++;
	}
	bufResposta[indBufResposta++] = '"';
	bufResposta[indBufResposta++] = ':';
	bufResposta[indBufResposta++] = '"';
	while (*param) {
		bufResposta[indBufResposta++] = *param++;
	}
	bufResposta[indBufResposta++] = '"';
}

// ---------------------------------------------
void addIntResposta(const char *cmd, int valor) 
{
	char strValor[10];
	char *pStr = strValor;


	sprintf(strValor, "%d", valor);
	bufResposta[indBufResposta++] = ',';
	bufResposta[indBufResposta++] = '"';
	while (*cmd) {
		bufResposta[indBufResposta++] = *cmd++;
	}
	bufResposta[indBufResposta++] = '"';
	bufResposta[indBufResposta++] = ':';
	while (*pStr) {
		bufResposta[indBufResposta++] = *pStr++;
	}
}

// ---------------------------------------------
void sendResposta(void)
{

	bufResposta[indBufResposta++] = '}';
	bufResposta[indBufResposta] = '\0';

	Serial.println(bufResposta);
}


//========================================================================================
// trataValoresGenericos - Trata valores diversos recebidos no par "action"/"valor"
//========================================================================================
int trataValoresGenericos(char *valor)
{	
	int ret=0;

	if ( iComando == CMD_LED  ) {
		// N,001
		// 01234
		valorP1 = atoi(&valor[0]);
		valorP2 = atoi(&valor[2]);
	} else if ( iComando == CMD_ALARM  ) {
		// N,1 (On)
		// N,0 (Off)
		valorP1 = atoi(&valor[0]);
		valorP2 = atoi(&valor[2]);
	} else if ( iComando == CMD_EJECT ) {
		// N,001
		// 01234
		valorP1 = atoi(&valor[0]);
		valorP2 = atoi(&valor[2]);
	} else if ( iComando == CMD_CONFIG ) {
		valorTipo = valor[0]; // 'S' para Sensores , 'B' para Bombas

		if (valorTipo == 'S') {
			// S,001,002,003,004
			// 012345678901234567890
			valorP1 = atoi(&valor[2]);
			valorP2 = atoi(&valor[6]);
			valorP3 = atoi(&valor[10]);
			valorP3 = atoi(&valor[14]);
		} else if (valorTipo == 'B') {
			// S,0001,0002,0003,0004
			// 012345678901234567890
			valorP1 = atoi(&valor[2]);
			valorP2 = atoi(&valor[7]);
			valorP3 = atoi(&valor[12]);
			valorP4 = atoi(&valor[17]);
		}
	}

	
	
	return ret;
}

//========================================================================================
// processaPar - Trata pares de chaves
//========================================================================================
void processaPar(char *token, char *valor) 
{	

	if ( token[0] == 'c'  ) {
		if ( valor[3] == 's' )  {	// fw_status_rq
			iComando = CMD_STATUS_RQ;
			return;
		}
	} else if ( token[0] == 'a' ) {
		if ( valor[3] == 'q' )  { // question
			iComando = CMD_STATUS_RQ;
			return;
		}
	} else if ( token[0] == 'p' ) { // packetNumber
		packetNumber = atoi(valor);
		if (packetNumber == 1 ) {
			numPktResp = 0; // Para resincronizar as respostas
			zeraControleEstados();
		}
		return;
	} else if ( token[0] == 'h' ) { // hour
		strcpy(strHora, valor);
		return;
	}
	
	if ( strcmp(token, "cmd") == 0 ) {
		if 		( strcmp(valor, "fw_restart"   ) == 0 ) { iComando = CMD_RESTART; } 
		else if ( strcmp(valor, "fw_status_rq" ) == 0 ) { iComando = CMD_STATUS_RQ;} 
		else if ( strcmp(valor, "fw_eject"     ) == 0 ) { iComando = CMD_EJECT; } 
		else if ( strcmp(valor, "fw_play"      ) == 0 ) { iComando = CMD_PLAY; } 
		else if ( strcmp(valor, "fw_led"       ) == 0 ) { iComando = CMD_LED; } 
		else if ( strcmp(valor, "fw_alarm"     ) == 0 ) { iComando = CMD_ALARM; } 
		else if ( strcmp(valor, "fw_config"    ) == 0 ) { iComando = CMD_CONFIG;} 
		else { iComando = 0; }
	} else if ( strcmp(token, "action") == 0 ) {
		if      ( strcmp(valor, "reset"   ) == 0 ) { iAction = ACTION_RESET; } 
		else if ( strcmp(valor, "on"      ) == 0 ) { iAction = ACTION_ON;} 
		else if ( strcmp(valor, "off"     ) == 0 ) { iAction = ACTION_OFF;} 
		else if ( strcmp(valor, "question") == 0 ) { iAction = ACTION_QUESTION;} 
		else if ( strcmp(valor, "simula5" ) == 0 ) { iAction = SIMULA5REAIS; } 
		else if ( strcmp(valor, "simula10") == 0 ) { iAction = SIMULA10REAIS;} 
		else if ( strcmp(valor, "simula20") == 0 ) { iAction = SIMULA20REAIS;} 
		else if ( strcmp(valor, "simula50") == 0 ) { iAction = SIMULA50REAIS;}
		else {
			iAction = trataValoresGenericos(valor);
		}
	} else if ( strcmp(token, "packetNumber") == 0 ) {
		packetNumber = atoi(valor);
		if (packetNumber == 1 ) {
			numPktResp = 0; // Para resincronizar as respostas
			zeraControleEstados();
		}
	} else if ( strcmp(token, "hour") == 0 ) {
		strcpy(strHora, valor);
	}
	
	return;
}



//========================================================================================
// processaLinha (Trata linhas recebidas do Tablet)
//========================================================================================
void processaLinha(void)
{ 
	const char *strRet = "";
	const char *action = "????";
	const char *status = "zzz";
	static int piscaLedIndicaRecebendoPacotes;


	// Pisca LED_1 para indicar que esta recebendo pacotes
	digitalWrite(LED_1, (piscaLedIndicaRecebendoPacotes++ & 0x1));


	switch ( iComando ) {

		case CMD_CONFIG :
			if ( valorTipo == 'S' ) { 
				// Configura distancia dos sensores
				action = trataConfigDistanciaSensores(valorP1, valorP2, valorP3, valorP4);
				// Sempre ao recebermos um pacote de config, vamos zerar o contador de pacotes
				// Isso decorre em função de sempre que reconectamos mandamos um pacote de config
				numPktResp = 0;
			} else if ( valorTipo == 'B' ){
				// Configura tempo de atuação das bombas
				action = trataConfigDasBombas(valorP1, valorP2, valorP3, valorP4);
			} else {
				action = "";
				valorP1=valorP2=valorP3=valorP4=0; 
			}

			
			startResposta("fw_config");
			addStrResposta("action", action);
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addIntResposta("p1", valorP1) ;
			addIntResposta("p2", valorP2);
			addIntResposta("p3", valorP3);
			addIntResposta("p4", valorP4);
			addStrResposta("hour", strHora);
			addStrResposta("ret", "ok");
			sendResposta();
			break;


		case CMD_RESTART :
			reinicializando = 3;
			startResposta("fw_restart");
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addStrResposta("hour", strHora);
			addStrResposta("ret", "ok");
			sendResposta();
			break;

		case CMD_STATUS_RQ :
			strRet = "ok";
			// para tratar reset
			if (reinicializando > 0  ) {
				reinicializando--;
				strRet = "busy";
			}
			startResposta("fw_status_rq");
			addStrResposta("action", "question");
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addIntResposta("error_n", 0); 
			addIntResposta("f1", readSensorAnalogico(1, distanciaMediaSensor1)) ; // Só pra poder controlar a geração ou não de febre
			addIntResposta("f2", readSensorAnalogico(2, 0)) ;
			
			addIntResposta("s1", readSensor(1)) ;
			addIntResposta("s2", readSensor(2));
			addIntResposta("s3", readSensor(3));
			addIntResposta("s4", readSensor(4));
#if 0			
			addIntResposta("b1", readBalanca(1));
			addIntResposta("b2", readBalanca(2));
			addIntResposta("b3", readBalanca(3));
#endif			

			addIntResposta("o1", distanciaMediaSensor1);
			addIntResposta("o2", distanciaMediaSensor2);
			addIntResposta("o3", distanciaLidaSensor1);
			addIntResposta("o4", distanciaLidaSensor2);
			
			addStrResposta("hour", strHora);
			addStrResposta("ret", (erro == 0) ? strRet : "error");

			sendResposta();
			break;

		case CMD_EJECT :
			strRet = "ok";
			action = trataEject(valorP1, valorP2);
			startResposta("fw_eject");
			addStrResposta("action", action);
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addIntResposta("error_n", 0);
			addStrResposta("hour", strHora);
			addStrResposta("ret", (erro == 0) ? strRet : "error");
			sendResposta();
			break;

		case CMD_PLAY :
			break;



		case CMD_LED :
			action = trataLed(valorP1, valorP2);
			startResposta("fw_led");
			addStrResposta("action", action);
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addStrResposta("hour", strHora);
			addStrResposta("ret", (erro == 0) ? strRet : "error");
			sendResposta();
			break;


		case CMD_ALARM :
			action = trataAlarm(valorP1, valorP2);
			startResposta("fw_alarm");
			addStrResposta("action", action);
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addStrResposta("hour", strHora);
			addStrResposta("ret", (erro == 0) ? strRet : "error");
			sendResposta();
			break;

		default:
			startResposta("fw_nack");
			addIntResposta("numPktResp", numPktResp);
			addIntResposta("packetNumber", packetNumber);
			addIntResposta("qtdNacks", ++pacotesNaoReconhecidos);
			sendResposta();
			break;
	}
}


//========================================================================================
// processaSerial (Chamada no loop principal)
//========================================================================================
int indRXPacote = 0;

//char buffer[500];
//int indBuffer=0;

void processaSerial(void)
{
	static int estado = AGUARDANDO_START;

	char data = Serial.read(); 

	if (data == 0 ) return;

	if (data == 0x2 ) { // STX
		suspendeLeituraDosSensoresUltrason = 1;// Vai começacr um pacote vamos suspender a leitura do leitor ultrasom
//		indBuffer=0;
//		buffer[indBuffer++] = '{';
//		buffer[indBuffer++] = '@';
		return;
	}
	
	if ( data == 0x3 ) { // ETX
//		if  (indBuffer > 10) {
//			sprintf(&buffer[indBuffer], " - %6d     ", contabytes);
//			buffer[indBuffer + 9] = '}';
//			buffer[indBuffer + 10] = '\0';
//			Serial.println(buffer);
//		}
//		indBuffer=0;
		return;
	}

//	buffer[indBuffer++] = ((data < 32) || (data == '{') || (data == '}') ) ? '.' : data;
	
	if ( data == '\n') {
		return;
	}

	if ( data == '}') {

		suspendeLeituraDosSensoresUltrason = 0; // Acabou o pacote pode voltar a tratar leitor ultrasom

		if (packetNumber == 1 ) {
			zeraControleEstados();
		}

		processaLinha( );

		if (packetNumber == 1 ) {
			zeraTudo();
		}
		estado = AGUARDANDO_START;
		flagTokenValor = 0;
		return;
	}

	switch (estado) {
	case AGUARDANDO_START :
		if ( data == '{') {
			zeraNovoPacote();
			estado = AGUARDANDO_ABRE_ASPAS;
		}
		break;
	case AGUARDANDO_ABRE_ASPAS :
		if ( data == '"') {
			flagValorNumerico = 0;
			if ( flagTokenValor == 0 ) {
				estado = AGUARDANDO_TOKEN;
			} else {
				estado = AGUARDANDO_VALOR;
			}
			indRXPacote = 0;
			strValor[indRXPacote] = '\0';
		} else {
			if ( flagTokenValor == 1) {
				if ( (data >= '0') && (data <= '9')) {
					flagValorNumerico = 1;
					estado = AGUARDANDO_VALOR;
					indRXPacote = 0;
					strValor[indRXPacote++] = data;
					strValor[indRXPacote] = '\0';
				}
			}
		}
		break;
		
	case AGUARDANDO_TOKEN :
		if ( data == '"') {
			estado = AGUARDANDO_DOIS_PONTOS;
		} else {
			strToken[indRXPacote++] = data;
			strToken[indRXPacote] = '\0';
		}
		break;
		
	case AGUARDANDO_DOIS_PONTOS :
		if ( data == ':') {
			estado = AGUARDANDO_ABRE_ASPAS;
			flagTokenValor = 1;
		}
		break;
		
	case AGUARDANDO_VALOR :
		if ( data == '"') {
			estado = AGUARDANDO_VIRGULA;
			processaPar(strToken, strValor);
			flagTokenValor = 0;
		} else {
			if (flagValorNumerico  ) {
				if ( data == ',') {
					processaPar(strToken, strValor);
					flagTokenValor = 0;
					estado = AGUARDANDO_ABRE_ASPAS;
					break;
				}
			}
			strValor[indRXPacote++] = data;
			strValor[indRXPacote] = '\0';
		}
		break;
		
	case AGUARDANDO_VIRGULA :
		if ( data == ',') {
			estado = AGUARDANDO_ABRE_ASPAS;
			flagTokenValor = 0;
		}
		break;
	}
	
}	



void zeraControleEstados()
{
	statusPlay = 0;
	statusDemo = 0;
	fsmStateDemo = 0;
	reinicializando = 0;
}


void zeraTudo(void)
{
	pacotesNaoReconhecidos = 0;

	zeraControleEstados();
	zeraNovoPacote();
}

void zeraNovoPacote(void)
{
	indRXPacote = 0;
	erro = 0;
	flagValorNumerico = 0;
	strToken[0] = '\0';
	strValor[0] = '\0';
	strHora[0] = '\0';

	strCmd[0] = '\0';
	strAction[0] = '\0';

	flagTokenValor = 0;
	packetNumber = 0;
	iComando = 0;
	iAction = 0;

	valorP1 = 0;
	valorP2 = 0;
	valorP3 = 0;
	valorP4 = 0;
}
