-- V19: MFA nunca foi implementado; as colunas saem junto com o codigo.

ALTER TABLE usuarios DROP COLUMN IF EXISTS mfa_enabled, DROP COLUMN IF EXISTS mfa_secret;
