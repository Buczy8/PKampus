import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { verifyEmail } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { FieldDescription } from '@/components/ui/field'

type VerifyState =
  | { status: 'loading' }
  | { status: 'success'; message: string }
  | { status: 'error'; message: string }

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [state, setState] = useState<VerifyState>({ status: 'loading' })

  useEffect(() => {
    if (!token?.trim()) {
      setState({
        status: 'error',
        message: 'Missing verification token. Open the link from your email again.',
      })
      return
    }

    let cancelled = false

    verifyEmail(token)
      .then((response) => {
        if (!cancelled) {
          setState({ status: 'success', message: response.message })
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setState({
            status: 'error',
            message: getApiErrorMessage(error, 'Email verification failed'),
          })
        }
      })

    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <main className="flex min-h-svh w-full items-center justify-center p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="text-center text-2xl font-medium tracking-tight">
          <span className="uppercase">Pk</span>ampus
        </p>
        <Card>
          <CardHeader>
            <CardTitle>Email verification</CardTitle>
            <CardDescription>
              {state.status === 'loading'
                ? 'Confirming your email address…'
                : state.status === 'success'
                  ? 'Your email has been confirmed'
                  : 'We could not verify this link'}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {state.status === 'loading' ? (
              <p className="text-sm text-muted-foreground">Please wait a moment.</p>
            ) : (
              <p className="text-sm text-muted-foreground">{state.message}</p>
            )}

            {state.status !== 'loading' ? (
              <Button asChild>
                <Link to="/login">Go to login</Link>
              </Button>
            ) : null}

            {state.status === 'error' ? (
              <FieldDescription className="text-center">
                Need a new account? <Link to="/register">Sign up</Link>
              </FieldDescription>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
