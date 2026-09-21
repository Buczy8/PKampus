import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'

const PASSWORD_PATTERN = /^(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{8,}$/

export function validateNewPassword(
  newPassword: string,
  confirmPassword: string,
): string | undefined {
  if (!newPassword) return 'Podaj nowe hasło'
  if (!PASSWORD_PATTERN.test(newPassword)) {
    return 'Hasło: min. 8 znaków, wielka litera, cyfra i znak specjalny'
  }
  if (newPassword !== confirmPassword) return 'Hasła nie są zgodne'
  return undefined
}

type ChangePasswordFieldsProps = {
  newPassword: string
  confirmPassword: string
  onNewPasswordChange: (value: string) => void
  onConfirmPasswordChange: (value: string) => void
  disabled?: boolean
  newPasswordId?: string
  confirmPasswordId?: string
}

export function ChangePasswordFields({
  newPassword,
  confirmPassword,
  onNewPasswordChange,
  onConfirmPasswordChange,
  disabled = false,
  newPasswordId = 'new-password',
  confirmPasswordId = 'confirm-password',
}: ChangePasswordFieldsProps) {
  return (
    <>
      <Field>
        <FieldLabel htmlFor={newPasswordId}>Nowe hasło</FieldLabel>
        <Input
          id={newPasswordId}
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => onNewPasswordChange(e.target.value)}
          disabled={disabled}
        />
      </Field>
      <Field>
        <FieldLabel htmlFor={confirmPasswordId}>Powtórz nowe hasło</FieldLabel>
        <Input
          id={confirmPasswordId}
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => onConfirmPasswordChange(e.target.value)}
          disabled={disabled}
        />
      </Field>
    </>
  )
}
