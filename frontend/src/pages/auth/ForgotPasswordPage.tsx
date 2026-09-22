import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { forgotPassword } from '@/api/auth'
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
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'

function validateEmail(email: string): string | undefined {
  if (!email.trim()) return 'Adres e-mail jest wymagany'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Niepoprawny format adresu e-mail'
  return undefined
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSubmitted, setIsSubmitted] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    const emailError = validateEmail(email)
    if (emailError) {
      setError(emailError)
      return
    }

    setIsSubmitting(true)
    try {
      await forgotPassword(email.trim())
      setIsSubmitted(true)
    } catch (err) {
      setError(getApiErrorMessage(err, 'Wystąpił błąd podczas wysyłania linku resetującego'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-svh w-full items-center justify-center p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="text-center text-2xl font-medium tracking-tight">
          <span className="uppercase">Pk</span>ampus
        </p>
        <Card>
          <CardHeader>
            <CardTitle>Reset hasła</CardTitle>
            <CardDescription>
              {isSubmitted
                ? 'Sprawdź swoją skrzynkę pocztową'
                : 'Wprowadź swój adres e-mail, aby otrzymać link do zresetowania hasła.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isSubmitted ? (
              <div className="flex flex-col gap-4">
                <div className="rounded-md bg-muted/60 p-4 text-sm text-muted-foreground leading-relaxed">
                  Jeśli podany adres e-mail (<strong>{email}</strong>) jest zarejestrowany w systemie, wysłaliśmy na niego wiadomość z linkiem do zmiany hasła.
                  <br /><br />
                  Link jest ważny przez <strong>15 minut</strong>.
                </div>
                <Button asChild className="w-full">
                  <Link to="/login">Powrót do logowania</Link>
                </Button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} noValidate>
                <FieldGroup>
                  <Field data-invalid={Boolean(error) || undefined}>
                    <FieldLabel htmlFor="email">Adres e-mail</FieldLabel>
                    <Input
                      id="email"
                      type="email"
                      placeholder="student@student.pk.edu.pl"
                      autoComplete="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      aria-invalid={Boolean(error)}
                      disabled={isSubmitting}
                    />
                    {error ? <FieldError>{error}</FieldError> : null}
                  </Field>
                  <Field>
                    <Button type="submit" disabled={isSubmitting} className="w-full">
                      {isSubmitting ? 'Wysyłanie linku…' : 'Wyślij link resetujący'}
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
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
