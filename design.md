Baue mir eine Python-Anwendung, die Inhalte aus der Zwischenablage ausliest und an ein LLM sendet.

Das Programm soll im Hintergrund laufen und bei Betätigung der Tastenkombination Shift + Control den aktuellen Inhalt der Zwischenablage 
an das LLM übermitteln. 

Die Antwort des LLMs soll anschließend automatisch an der Stelle ausgegeben werden, an der sich der Cursor aktuell befindet.


technik:




Das LLM ist gemini
 Erzeuge Log-Ausgaben für jeden Schritt.
 Lese API-Key aus der Umgebungsvariable GEMINI_API_KEY ein. 
Bei erkennterner Tastenkommunations soll der Inhalt geloggt werden, 
dann logge auch den Request-Response und loggen die Antwort.



