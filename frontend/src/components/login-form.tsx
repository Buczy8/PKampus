import { useState, type ComponentProps, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { cn } from 'cn'

import { login } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
import { isAdminRole, isSuperAdminRole } from '@/api/types'
import { setAuthTokens } from '@/lib/auth-storage'
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
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'

type LoginErrors = {
  email?: string
  password?: string
  form?: string
}

function validateEmail(email: string): string | undefined {
  if (!email.trim()) return 'Email is required'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Invalid email address format'
  return undefined
}

export function LoginForm({
  className,
  ...props
}: ComponentProps<'div'>) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<LoginErrors>({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const nextErrors: LoginErrors = {
      email: validateEmail(email),
      password: password ? undefined : 'Password is required',
    }

    setErrors(nextErrors)
    if (nextErrors.email || nextErrors.password) return

    setIsSubmitting(true)
    try {
      const auth = await login({ email: email.trim(), password })
      setAuthTokens(auth.token, auth.refreshToken)
      // Seed cache so ProtectedRoute does not reuse a previous failed /me error.
      queryClient.setQueryData(['auth', 'me'], auth.user)
      const destination = isSuperAdminRole(auth.user.role)
        ? '/superadmin'
        : isAdminRole(auth.user.role)
          ? '/admin'
          : '/dashboard'
      navigate(destination, { replace: true })
    } catch (error) {
      const message = getApiErrorMessage(error, 'Login failed')
      if (/awaiting residency approval|PENDING_APPROVAL/i.test(message)) {
        navigate('/pending-approval')
        return
      }
      setErrors({ form: message })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className={cn('flex flex-col gap-6', className)} {...props}>
      <p className="text-center text-2xl font-medium tracking-tight">
        <span className="uppercase">Pk</span>ampus
      </p>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
          <CardDescription>
            Enter your email below to login to your account
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} noValidate>
            <FieldGroup>
              <Field data-invalid={Boolean(errors.email) || undefined}>
                <FieldLabel htmlFor="email">Email</FieldLabel>
                <Input
                  id="email"
                  type="email"
                  placeholder="m@example.com"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  aria-invalid={Boolean(errors.email)}
                  disabled={isSubmitting}
                />
                {errors.email ? <FieldError>{errors.email}</FieldError> : null}
              </Field>
              <Field data-invalid={Boolean(errors.password) || undefined}>
                <div className="flex items-center">
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <a
                    href="#"
                    className="ml-auto inline-block text-sm underline-offset-4 hover:underline"
                    onClick={(e) => e.preventDefault()}
                  >
                    Forgot your password?
                  </a>
                </div>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-invalid={Boolean(errors.password)}
                  disabled={isSubmitting}
                />
                {errors.password ? <FieldError>{errors.password}</FieldError> : null}
              </Field>
              {errors.form ? (
                <Field data-invalid>
                  <FieldError>{errors.form}</FieldError>
                </Field>
              ) : null}
              <Field>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Logging in…' : 'Login'}
                </Button>
                <FieldDescription className="text-center">
                  Don&apos;t have an account? <Link to="/register">Sign up</Link>
                </FieldDescription>
              </Field>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
