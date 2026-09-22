/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
  readonly VITE_KEYCLOAK_CLIENT_ID?: string;
  readonly VITE_API_TARGET?: string;
  /** `'1'` forces the mi-assistant path, same escape hatch as mobile's MSB_VRM_DISABLED. */
  readonly VITE_MSB_VRM_DISABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
