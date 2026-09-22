import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { changePassword } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
import { homePathForRole } from '@/api/types'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ChangePasswordFields, validateNewPassword } from '@/components/auth/ChangePasswordFields'

export function ChangePasswordPage({ forced = false }: { forced?: boolean }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    const validationError = validateNewPassword(newPassword, confirmPassword)
    if (!currentPassword) {
      setError('Podaj aktualne hasło')
      return
    }
    if (validationError) {
      setError(validationError)
      return
    }

    setIsSubmitting(true)
    try {
      const profile = await changePassword({ currentPassword, newPassword })
      queryClient.setQueryData(['auth', 'me'], profile)
      navigate(homePathForRole(profile.role), { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err, 'Nie udało się zmienić hasła'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-svh w-full items-center justify-center p-6">
      <div className="flex w-full max-w-md flex-col gap-6">
        <p className="text-center text-2xl font-medium tracking-tight">
          <span className="uppercase">Pk</span>ampus
        </p>
        <Card>
          <CardHeader>
            <CardTitle>
              {forced ? 'Wymagana zmiana hasła' : 'Zmiana hasła'}
            </CardTitle>
            <CardDescription>
              {forced
                ? 'Konto zostało utworzone z hasłem tymczasowym. Ustaw własne hasło, aby kontynuować.'
                : 'Wprowadź aktualne hasło oraz nowe hasło zgodne z polityką bezpieczeństwa.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} noValidate>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="current-password">Aktualne hasło</FieldLabel>
                  <Input
                    id="current-password"
                    type="password"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    disabled={isSubmitting}
                  />
                </Field>
                <ChangePasswordFields
                  newPassword={newPassword}
                  confirmPassword={confirmPassword}
                  onNewPasswordChange={setNewPassword}
                  onConfirmPasswordChange={setConfirmPassword}
                  disabled={isSubmitting}
                />
                {error ? (
                  <Field data-invalid>
                    <FieldError>{error}</FieldError>
                  </Field>
                ) : null}
                <Field>
                  <Button type="submit" disabled={isSubmitting} className="w-full">
                    {isSubmitting ? 'Zapisywanie…' : 'Ustaw nowe hasło'}
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
