import os
import requests

def check_api_quota(project_id, service_name):
    """
    https://chatgpt.com/c/67729eb0-43b4-8005-982e-332cd81879d2

    Prüft, ob noch Limits und Kontingente für eine API vorhanden sind.

    :param project_id: Google Cloud-Projekt-ID
    :param service_name: Name der API (z. B. "aiplatform.googleapis.com")
    :return: Boolean - True, wenn noch Limits vorhanden sind, sonst False
    """
    try:
        # API-Schlüssel aus der Umgebungsvariable lesen
        api_key = os.getenv("GEMEINI_API_KEY")
        _
        if not api_key:
            raise ValueError("Umgebungsvariable 'API_KEY' ist nicht gesetzt.")

        # URL für die Service Usage API
        url = f"https://serviceusage.googleapis.com/v1/projects/{project_id}/services/{service_name}/consumerQuotaMetrics?key={api_key}"

        # Anfrage an die API senden
        response = requests.get(url)
        response.raise_for_status()
        data = response.json()

        # Überprüfen, ob Quota verfügbar ist
        for metric in data.get("metrics", []):
            for limit in metric.get("consumerQuotaLimits", []):
                usage = limit.get("usage", {}).get("consumed", 0)
                limit_value = limit.get("limit", 0)
                if usage < limit_value:
                    return True  # Es sind noch Limits verfügbar

        return False  # Keine Limits mehr verfügbar
    except Exception as e:
        print(f"Fehler bei der API-Abfrage: {e}")
        return False


# Beispielaufruf
if __name__ == "__main__":
    project_id = "391500694148"
    service_name = "aiplatform.googleapis.com"  # Beispiel: Gemini API

    has_quota = check_api_quota(project_id, service_name)
    if has_quota:
        print("Limits sind verfügbar. Sie können weitere Abfragen durchführen.")
    else:
        print("Limits sind ausgeschöpft. Keine weiteren Abfragen möglich.")
