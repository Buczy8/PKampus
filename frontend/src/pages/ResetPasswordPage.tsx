import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { resetPassword, verifyResetToken } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
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
} from '@/components/ui/field'
import { ChangePasswordFields, validateNewPassword } from '@/components/auth/ChangePasswordFields'

type TokenVerificationState =
  | { status: 'loading' }
  | { status: 'valid'; maskedEmail?: string }
  | { status: 'invalid'; error: string }

export function ResetPasswordPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [verification, setVerification] = useState<TokenVerificationState>({ status: 'loading' })
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)

  useEffect(() => {
    if (!token?.trim()) {
      setVerification({
        status: 'invalid',
        error: 'Brak tokenu resetującego hasło. Użyj linku otrzymanego w wiadomości e-mail.',
      })
      return
    }

    let cancelled = false

    verifyResetToken(token.trim())
      .then((res) => {
        if (cancelled) return
        if (res.valid) {
          setVerification({ status: 'valid', maskedEmail: res.maskedEmail })
        } else {
          setVerification({
            status: 'invalid',
            error: 'Link do resetowania hasła wygasł lub został już wykorzystany.',
          })
        }
      })
      .catch((err) => {
        if (cancelled) return
        setVerification({
          status: 'invalid',
          error: getApiErrorMessage(err, 'Link do resetowania hasła jest nieprawidłowy lub wygasł.'),
        })
      })

    return () => {
      cancelled = true
    }
  }, [token])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setError(null)

    const validationError = validateNewPassword(newPassword, confirmPassword)
    if (validationError) {
      setError(validationError)
      return
    }

    setIsSubmitting(true)
    try {
      await resetPassword(token.trim(), newPassword)
      setIsSuccess(true)
    } catch (err) {
      setError(getApiErrorMessage(err, 'Nie udało się zresetować hasła. Link mógł wygasnąć.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-svh w-full items-center justify-center p-6 md:p-10">
      <div className="flex w-full max-w-md flex-col gap-6">
        <p className="text-center text-2xl font-medium tracking-tight">
          <span className="uppercase">Pk</span>ampus
        </p>
        <Card>
          <CardHeader>
            <CardTitle>Nowe hasło</CardTitle>
            <CardDescription>
              {verification.status === 'loading'
                ? 'Sprawdzanie ważności linku resetującego…'
                : verification.status === 'invalid'
                  ? 'Nieprawidłowy lub wygasły link'
                  : isSuccess
                    ? 'Hasło zostało zmienione'
                    : 'Wprowadź nowe hasło do swojego konta.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {verification.status === 'loading' ? (
              <p className="text-sm text-muted-foreground">Proszę czekać…</p>
            ) : null}

            {verification.status === 'invalid' ? (
              <div className="flex flex-col gap-4">
                <p className="text-sm text-destructive">{verification.error}</p>
                <Button asChild className="w-full">
                  <Link to="/forgot-password">Poproś o nowy link</Link>
                </Button>
                <div className="text-center text-sm">
                  <Link
                    to="/login"
                    className="text-muted-foreground underline-offset-4 hover:underline hover:text-foreground"
                  >
                    Powrót do logowania
                  </Link>
                </div>
              </div>
            ) : null}

            {verification.status === 'valid' && isSuccess ? (
              <div className="flex flex-col gap-4">
                <div className="rounded-md bg-muted/60 p-4 text-sm text-foreground">
                  Twoje hasło zostało pomyślnie zaktualizowane. Wszystkie wcześniejsze sesje zostały zakończone. Możesz teraz zalogować się przy użyciu nowego hasła.
                </div>
                <Button type="button" onClick={() => navigate('/login')} className="w-full">
                  Przejdź do logowania
                </Button>
              </div>
            ) : null}

            {verification.status === 'valid' && !isSuccess ? (
              <form onSubmit={handleSubmit} noValidate>
                <FieldGroup>
                  {verification.maskedEmail ? (
                    <div className="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
                      Resetujesz hasło dla konta: <strong>{verification.maskedEmail}</strong>
                    </div>
                  ) : null}

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
                      {isSubmitting ? 'Zapisywanie…' : 'Zmień hasło'}
                    </Button>
                  </Field>

                  <div className="text-center text-sm">
                    <Link
                      to="/login"
                      className="text-muted-foreground underline-offset-4 hover:underline hover:text-foreground"
                    >
                      Pamiętasz hasło? Zaloguj się
                    </Link>
                  </div>
                </FieldGroup>
              </form>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
